package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

/** Public-API capability adapter: also compiles against the published 3.0.15 generator. */
final class P4SourceMapping {
  private P4SourceMapping() {}

  record Selection(Token token, TinyExpressionP4AST ast, P4SourceText sourceText) {}

  static Selection select(Token token, String preferred, String parserSource) {
    return select(TinyExpressionP4Mapper.class, token, preferred, parserSource);
  }

  static Selection select(Class<?> mapper, Token token, String preferred, String parserSource) {
    Method selector;
    try {
      selector = mapper.getMethod("selectParsedTokenWithSourceMap", Token.class, String.class);
    } catch (NoSuchMethodException absent) {
      Object legacy = invoke(method(mapper, "mapParsedToken", Token.class, String.class), null, token, preferred);
      return new Selection((Token) accessor(legacy, "token"),
          (TinyExpressionP4AST) accessor(legacy, "ast"), P4SourceText.lexicalOnly());
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
