package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

/** Public-API capability adapter: also compiles against the published 3.0.15 generator. */
final class P4SourceMapping {
  private P4SourceMapping() {}

  enum EntryPoint { ROOT, ALTERNATE }

  record Selection(Token token, TinyExpressionP4AST ast, P4SourceText sourceText) {}

  static Selection select(Token token, String preferred, String parserSource) {
    return select(TinyExpressionP4Mapper.class, token, preferred, parserSource, EntryPoint.ROOT);
  }

  static Selection select(Class<?> mapper, Token token, String preferred, String parserSource) {
    return select(mapper, token, preferred, parserSource, EntryPoint.ROOT);
  }

  static Selection select(Token token, String preferred, String parserSource, EntryPoint entryPoint) {
    return select(TinyExpressionP4Mapper.class, token, token, preferred, parserSource, entryPoint);
  }

  static Selection select(
      Class<?> mapper, Token token, String preferred, String parserSource, EntryPoint entryPoint) {
    return select(mapper, token, token, preferred, parserSource, entryPoint);
  }

  static Selection select(Token token, Token legacyToken, String preferred,
      String parserSource, EntryPoint entryPoint) {
    return select(TinyExpressionP4Mapper.class, token, legacyToken, preferred, parserSource, entryPoint);
  }

  static Selection select(Class<?> mapper, Token token, Token legacyToken, String preferred,
      String parserSource, EntryPoint entryPoint) {
    String selectorName = entryPoint == EntryPoint.ALTERNATE
        ? "selectSubtreeTokenWithSourceMap" : "selectParsedTokenWithSourceMap";
    Method selector;
    try {
      selector = mapper.getMethod(selectorName, Token.class, String.class);
    } catch (NoSuchMethodException absent) {
      return selectLegacy(mapper, legacyToken, preferred, parserSource);
    }
    // Invocation failures or an incompatible new API must not silently retry legacy mapping.
    Object selected = invoke(selector, null, token, preferred);
    Object snapshot = accessor(selected, "sourceMap");
    Method lookup = method(snapshot.getClass(), "sourceSpanOf", Object.class);
    P4SourceText sourceText = P4SourceText.fromSnapshot(parserSource, node -> {
      Object result = invoke(lookup, snapshot, node);
      if (!(result instanceof Optional<?> span)) throw new IllegalStateException("Invalid source snapshot lookup");
      return span.map(value -> {
        if (!(value instanceof int[] offsets)) throw new IllegalStateException("Invalid source snapshot offsets");
        return offsets.clone();
      });
    });
    return new Selection((Token) accessor(selected, "token"),
        (TinyExpressionP4AST) accessor(snapshot, "ast"), sourceText);
  }

  /** Published-generator compatibility: copy the live mapper spans while holding its class lock. */
  private static Selection selectLegacy(
      Class<?> mapper, Token token, String preferred, String parserSource) {
    synchronized (mapper) {
      Object legacy = invoke(method(mapper, "mapParsedToken", Token.class, String.class),
          null, token, preferred);
      Token selectedToken = (Token) accessor(legacy, "token");
      TinyExpressionP4AST ast = (TinyExpressionP4AST) accessor(legacy, "ast");
      return new Selection(selectedToken, ast, snapshotLegacySource(mapper, ast, parserSource));
    }
  }

  private static P4SourceText snapshotLegacySource(
      Class<?> mapper, TinyExpressionP4AST ast, String parserSource) {
    Method lookup;
    try {
      lookup = mapper.getMethod("sourceSpanOf", Object.class);
    } catch (NoSuchMethodException absent) {
      return P4SourceText.lexicalOnly();
    }
    Map<Object, int[]> spans = new IdentityHashMap<>();
    var pending = new ArrayDeque<Object>();
    var seen = new IdentityHashMap<Object, Boolean>();
    pending.add(ast);
    while (!pending.isEmpty()) {
      Object value = pending.removeFirst();
      if (seen.put(value, Boolean.TRUE) != null) continue;
      if (value instanceof TinyExpressionP4AST) {
        Object result = invoke(lookup, null, value);
        if (!(result instanceof Optional<?> optional)) {
          throw new IllegalStateException("Invalid legacy source span lookup");
        }
        optional.ifPresent(span -> {
          if (!(span instanceof int[] offsets)) {
            throw new IllegalStateException("Invalid legacy source span offsets");
          }
          spans.put(value, normalizeLegacySpan(parserSource, offsets));
        });
      }
      enqueueChildren(value, pending);
    }
    return P4SourceText.fromSnapshot(parserSource, node -> {
      int[] span = spans.get(node);
      return span == null ? Optional.empty() : Optional.of(span.clone());
    });
  }

  /**
   * The published 3.0.15 mapper stores a code-point start plus a UTF-16 token length. Convert that
   * mixed representation to the code-point half-open interval used by {@link P4SourceText}.
   */
  private static int[] normalizeLegacySpan(String source, int[] offsets) {
    if (offsets.length != 2) return offsets.clone();
    int startCodePoint = offsets[0];
    int utf16Length = offsets[1] - startCodePoint;
    int sourceCodePoints = source.codePointCount(0, source.length());
    if (startCodePoint < 0 || startCodePoint > sourceCodePoints || utf16Length < 0) {
      return offsets.clone();
    }
    int startUtf16 = source.offsetByCodePoints(0, startCodePoint);
    long endUtf16Long = (long) startUtf16 + utf16Length;
    if (endUtf16Long > source.length()) return offsets.clone();
    int endUtf16 = (int) endUtf16Long;
    if (endUtf16 > 0 && endUtf16 < source.length()
        && Character.isHighSurrogate(source.charAt(endUtf16 - 1))
        && Character.isLowSurrogate(source.charAt(endUtf16))) {
      return offsets.clone();
    }
    return new int[]{startCodePoint, source.codePointCount(0, endUtf16)};
  }

  private static void enqueueChildren(Object value, ArrayDeque<Object> pending) {
    if (value instanceof Optional<?> optional) {
      optional.ifPresent(pending::addLast);
      return;
    }
    if (value instanceof Iterable<?> iterable) {
      for (Object child : iterable) if (child != null) pending.addLast(child);
      return;
    }
    if (value instanceof Map<?, ?> map) {
      for (var entry : map.entrySet()) {
        if (entry.getKey() != null) pending.addLast(entry.getKey());
        if (entry.getValue() != null) pending.addLast(entry.getValue());
      }
      return;
    }
    if (value.getClass().isArray()) {
      for (int i = 0; i < Array.getLength(value); i++) {
        Object child = Array.get(value, i);
        if (child != null) pending.addLast(child);
      }
      return;
    }
    if (value.getClass().isRecord()) {
      for (var component : value.getClass().getRecordComponents()) {
        Object child = invoke(component.getAccessor(), value);
        if (child != null) pending.addLast(child);
      }
    }
  }

  private static Object accessor(Object target, String name) {
    return invoke(method(target.getClass(), name), target);
  }

  private static Method method(Class<?> type, String name, Class<?>... arguments) {
    try {
      return type.getMethod(name, arguments);
    } catch (NoSuchMethodException error) {
      throw new IllegalStateException("Incompatible public mapper API: " + name, error);
    }
  }

  private static Object invoke(Method method, Object receiver, Object... arguments) {
    try {
      return method.invoke(receiver, arguments);
    } catch (InvocationTargetException error) {
      Throwable cause = error.getCause();
      if (cause instanceof RuntimeException runtime) throw runtime;
      if (cause instanceof Error fatal) throw fatal;
      throw new IllegalStateException("Mapper API failed: " + method.getName(), cause);
    } catch (IllegalAccessException error) {
      throw new IllegalStateException("Mapper API is not publicly accessible: " + method.getName(), error);
    }
  }
}
