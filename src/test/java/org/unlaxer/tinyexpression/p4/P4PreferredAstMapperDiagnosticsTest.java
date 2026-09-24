package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.dsl.runtime.ScopeStore;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.HasChildrenParser;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;

/**
 * Pins implementation details of the {@code classic} engine (unlaxer Classic, deprecated alias
 * {@code legacy}; {@link ClassicP4PreferredAstMapper}): deferred-diagnostics safety of the
 * combinator root graphs, the in-parse deadline listener and its retry budget. Scoped to
 * {@code classic} since 2.0.0 (issue #183): the default ubnfc engine has no combinator graph and
 * checks the deadline only before/after parsing, so these internals do not exist there. The
 * public-surface behaviour (messages, exception types) of both engines is pinned by
 * {@code UbnfcParityTest}. Remove together with {@code classic} in 3.0.
 */
public class P4PreferredAstMapperDiagnosticsTest {
  private static final String MEMOIZE = "tinyexpression.p4.memoize";

  private static List<Parser> roots() {
    return List.of(TinyExpressionP4Parsers.getRootParser(),
        Parser.get(TinyExpressionP4Parsers.BooleanExpressionParser.class),
        Parser.get(TinyExpressionP4Parsers.StringExpressionParser.class),
        Parser.get(TinyExpressionP4Parsers.ObjectExpressionParser.class));
  }

  @Test public void allRootGraphsAreDeferredDiagnosticsSafe() throws Exception {
    Set<Parser> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    var pending = new ArrayDeque<>(roots());
    while (!pending.isEmpty()) {
      Parser parser = pending.pop();
      if (!visited.add(parser)) continue;
      assertTrue(parser.getClass().getName(), isSafe(parser));
      // tinyexpression's shadow AbstractParser does not implement HasChildrenParser. Walk its
      // children too, so custom tokens inside constructed repetitions cannot escape this check.
      pending.addAll(parser.getChildren());
    }
  }

  @Test public void safetyWalkChecksShadowRepeatChildrenAndRejectsUnknownSubclasses() throws Exception {
    Parser repeat = new ZeroOrMore(new Probe());
    assertFalse("repeat uses shadow AbstractParser children", repeat instanceof HasChildrenParser);
    assertFalse(isSafe(repeat));
    assertFalse(isSafe(new Chain(new Probe())));
    assertTrue(isSafe(new StringLiteralParser()));
    assertFalse(isSafe(new StringLiteralParser() {}));
  }

  @Test(timeout = 5_000L) public void safetyWalkHandlesSharedNodesAndCycles() throws Exception {
    Parser word = new WordParser("ok");
    Parser repeat = new ZeroOrMore(word);
    repeat.getChildren().add(word);
    repeat.getChildren().add(repeat);
    assertTrue(isSafe(repeat));
    repeat.getChildren().add(new Probe());
    assertFalse(isSafe(repeat));
  }

  @Test public void classicDetailedPathRunsOnceWithEitherMemoizationPolicy() throws Exception {
    boolean available = compat() != null;
    if (System.getProperty("tinyexpression.expected.mapper.snapshot") != null) {
      assertEquals(Boolean.getBoolean("tinyexpression.expected.mapper.snapshot"), available);
    }
    System.out.println("P4 facade diagnostics API: " + (available ? "available" : "legacy fallback"));
    String previous = System.getProperty(MEMOIZE);
    try {
      for (boolean memoize : List.of(true, false)) {
        System.setProperty(MEMOIZE, Boolean.toString(memoize));
        for (String source : List.of("ok", "bad", "ok😀")) {
          var parser = new Probe();
          // Force the classic constructor on development too; on published exercise dispatch itself.
          Object result = available ? attemptClassic(parser, source) : attempt(parser, source, 0L);
          assertEquals(source.startsWith("ok"), field(result, "succeeded"));
          assertEquals(source.startsWith("ok") ? 2 : -1, field(result, "consumed"));
          assertEquals(1, parser.contexts.size());
          assertEquals(1, parser.closed);
          if (!memoize) assertFalse(parser.contexts.getFirst().isMemoizeEnabled());
          if (available) assertEquals("DETAILED", option(parser.contexts.getFirst(), "diagnostics"));
        }
      }
      assertEquals("Parse failed: @", assertThrows(IllegalArgumentException.class,
          () -> ClassicP4PreferredAstMapper.parseDetailed("@")).getMessage());
    } finally {
      if (previous == null) System.clearProperty(MEMOIZE); else System.setProperty(MEMOIZE, previous);
    }
  }

  private static Object attemptClassic(Parser parser, String source) throws Exception {
    return invoke(ClassicP4PreferredAstMapper.class.getDeclaredMethod(
        "parseWithRoot", Parser.class, String.class, long.class, Object.class),
        parser, source, 0L, null);
  }

  @Test public void successUsesOneDeferredContextWithEitherMemoizationPolicy() throws Exception {
    String previous = System.getProperty(MEMOIZE);
    try {
      for (boolean memoize : List.of(true, false)) {
        System.setProperty(MEMOIZE, Boolean.toString(memoize));
        try (var parser = new SafeProbe()) {
          Object result = attempt(parser, "ok", 0L);
          assertEquals(true, field(result, "succeeded"));
          assertEquals(2, field(result, "consumed"));
          assertEquals(1, parser.contexts.size());
          assertEquals("DETAILED_ON_FAILURE", option(parser.contexts.getFirst(), "diagnostics"));
          assertEquals(memoize ? "SAFE_FAILURES" : "OFF",
              option(parser.contexts.getFirst(), "memoization"));
          assertEquals(1, parser.closed);
        }
      }
    } finally {
      if (previous == null) System.clearProperty(MEMOIZE); else System.setProperty(MEMOIZE, previous);
    }
  }

  @Test public void syntaxFailureAndTrailingInputRetryWithFreshDetailedState() throws Exception {
    for (String source : List.of("bad", "ok😀")) {
      try (var parser = new SafeProbe()) {
        Object result = attempt(parser, source, System.nanoTime() + 5_000_000_000L);
        assertEquals(source.startsWith("ok"), field(result, "succeeded"));
        assertEquals(source.startsWith("ok") ? 2 : -1, field(result, "consumed"));
        assertEquals(2, parser.contexts.size());
        ParseContext first = parser.contexts.get(0), retry = parser.contexts.get(1);
        assertNotSame(first, retry);
        assertEquals("DETAILED_ON_FAILURE", option(first, "diagnostics"));
        assertEquals("DETAILED", option(retry, "diagnostics"));
        assertEquals(option(first, "memoization"), option(retry, "memoization"));
        if (first.isMemoizeEnabled()) {
          assertNotSame(field(first, "getPackratMemoTable"), field(retry, "getPackratMemoTable"));
        }
        assertEquals(2, parser.closed);
        assertEquals(List.of(true, true), parser.deadlineRegistered);
        if (source.equals("bad")) {
          assertTrue(expectedTokens(first).isEmpty());
          assertFalse(expectedTokens(retry).isEmpty());
        }
      }
    }
  }

  @Test public void undeclaredParserKeepsDetailedDiagnosticsWithoutRetry() throws Exception {
    assumeNewApi();
    var parser = new Probe();
    attempt(parser, "bad", 0L);
    assertEquals(1, parser.contexts.size());
    assertEquals("DETAILED", option(parser.contexts.getFirst(), "diagnostics"));
    assertFalse(expectedTokens(parser.contexts.getFirst()).isEmpty());
  }

  @Test public void allRootsRetainDetailedAcceptanceAndConsumedLength() throws Exception {
    for (Parser root : roots()) {
      for (String source : List.of("1 + 2", "1 > 0 & 2 > 1", "'😀' + 'x'", "$x as object",
          "var $n as number set 3; $n + 4", "(1 +", "'😀' @", "")) {
        Object actual = attempt(root, source, 0L);
        Object detailed = attemptDetailed(root, source);
        assertEquals(root.getClass() + ": " + source, field(detailed, "succeeded"), field(actual, "succeeded"));
        assertEquals(field(detailed, "consumed"), field(actual, "consumed"));
        for (String tokenField : List.of("rootToken", "legacyToken")) {
          org.unlaxer.Token expectedToken = (org.unlaxer.Token) field(detailed, tokenField);
          org.unlaxer.Token actualToken = (org.unlaxer.Token) field(actual, tokenField);
          assertEquals(expectedToken == null ? null : expectedToken.getToken(),
              actualToken == null ? null : actualToken.getToken());
        }
      }
    }
  }

  @Test public void generatedDiagnosticsAndExceptionMessagesMatchDetailedPolicy() throws Exception {
    assumeNewApi();
    Object options = options(true), detailed = options(false);
    Class<?> optionsType = options.getClass();
    Method diagnose = TinyExpressionP4Mapper.class.getMethod("diagnose", String.class, optionsType);
    Method parse = TinyExpressionP4Mapper.class.getMethod("parseWithOptions", String.class, optionsType);
    for (String source : List.of("(1 +", "'😀' @", "var $n as number set 3;\n$n +", "")) {
      // Record equality checks every ParseDiagnostic field, including both expected lists.
      assertEquals(invoke(diagnose, source, detailed), invoke(diagnose, source, options));
      assertEquals(assertThrows(IllegalArgumentException.class,
          () -> invoke(parse, source, detailed)).getMessage(),
          assertThrows(IllegalArgumentException.class,
              () -> invoke(parse, source, options)).getMessage());
      assertEquals("Parse failed: " + source, assertThrows(IllegalArgumentException.class,
          () -> ClassicP4PreferredAstMapper.parseDetailed(source)).getMessage());
    }
  }

  @Test public void expiredDeadlineAbortsFirstPassWithoutRetry() throws Exception {
    try (var parser = new SafeProbe()) {
      assertThrows(P4PreferredAstMapper.ParseDeadlineExceededException.class,
          () -> attempt(parser, "bad", System.nanoTime() - 1L));
      assertEquals(1, parser.contexts.size());
    }
  }

  @Test public void retryDeadlineListenerStillAbortsParsing() throws Exception {
    try (var parser = new SafeProbe()) {
      parser.expireRetry = true;
      assertThrows(P4PreferredAstMapper.ParseDeadlineExceededException.class,
          () -> attempt(parser, "bad", System.nanoTime() + 150_000_000L));
      assertEquals(2, parser.contexts.size());
    }
  }

  @Test public void retryReceivesTheOriginalRemainingTimeBudget() throws Exception {
    try (var parser = new SafeProbe()) {
      parser.pauseMillis = 600L;
      long deadline = System.nanoTime() + 1_000_000_000L;
      Object result = attempt(parser, "bad", deadline);
      assertEquals(false, field(result, "succeeded"));
      assertEquals(2, parser.contexts.size());
      assertEquals(2, parser.closed);
      assertTrue("two passes together exceed the initial deadline", System.nanoTime() > deadline);
    }
  }

  // Probe observations do not affect acceptance; each attempt mutates only its own ScopeStore.
  private static class Probe extends WordParser {
    final List<ParseContext> contexts = new ArrayList<>();
    final List<Boolean> deadlineRegistered = new ArrayList<>();
    int closed;
    boolean expireRetry;
    long pauseMillis;
    Probe() { super("ok"); }
    @Override public Parsed parse(ParseContext context, TokenKind kind, boolean invert) {
      assertEquals(contexts.size(), closed); // prior context must close before the retry opens
      assertFalse(ScopeStore.isDeclared(context, "probe"));
      ScopeStore.declare(context, "probe", 0);
      contexts.add(context);
      deadlineRegistered.add(context.getTransactionListenerByName().containsKey(
          Name.of(ClassicP4PreferredAstMapper.class, "parseDeadline")));
      addListener(context, new TransactionListener() {
        @Override public void setLevel(org.unlaxer.listener.OutputLevel level) {}
        @Override public void onOpen(ParseContext ignored) {}
        @Override public void onBegin(ParseContext ignored, Parser parser) {}
        @Override public void onClose(ParseContext ignored) { closed++; }
      });
      if (pauseMillis > 0L || (expireRetry && contexts.size() == 2)) {
        try { Thread.sleep(pauseMillis > 0L ? pauseMillis : 200L); } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
      }
      context.begin(this);
      Parsed parsed = super.parse(context, kind, invert);
      if (parsed.isSucceeded()) return new Parsed(context.commit(this, kind));
      context.rollback(this);
      return parsed;
    }
  }

  // Seed only this observed instance; graph tests above separately verify the real allowlist.
  private static final class SafeProbe extends Probe implements AutoCloseable {
    SafeProbe() throws Exception {
      assumeNewApi();
      safetyCache().put(this, true);
    }
    @Override public void close() throws Exception { safetyCache().remove(this); }
  }

  @SuppressWarnings("unchecked")
  private static Map<Parser, Boolean> safetyCache() throws Exception {
    var field = ClassicP4PreferredAstMapper.class.getDeclaredField("DEFERRED_DIAGNOSTICS_SAFE");
    field.setAccessible(true);
    return (Map<Parser, Boolean>) field.get(null);
  }

  private static boolean isSafe(Parser parser) throws Exception {
    return (boolean) invoke(ClassicP4PreferredAstMapper.class.getDeclaredMethod(
        "isDeferredDiagnosticsSafe", Parser.class), parser);
  }

  private static Object compat() throws Exception {
    var field = ClassicP4PreferredAstMapper.class.getDeclaredField("DIAGNOSTICS_COMPAT");
    field.setAccessible(true);
    return field.get(null);
  }

  private static void assumeNewApi() throws Exception {
    assumeTrue("published unlaxer has no deferred diagnostics API", compat() != null);
  }

  private static Object options(boolean safe) throws Exception {
    Object compat = compat();
    Method method = compat.getClass().getDeclaredMethod("options", boolean.class, boolean.class);
    method.setAccessible(true);
    return method.invoke(compat, true, safe);
  }

  private static String option(ParseContext context, String name) throws Exception {
    return field(field(context, "getOptions"), name).toString();
  }

  private static Set<?> expectedTokens(ParseContext context) throws Exception {
    return (Set<?>) field(field(context, "getParseFailureDiagnostics"), "getExpectedTokens");
  }

  private static void addListener(ParseContext context, TransactionListener listener) {
    try {
      ParseContext.class.getMethod("addMemoizationTransparentTransactionListener",
          Name.class, TransactionListener.class).invoke(context, Name.of("probeClose"), listener);
    } catch (NoSuchMethodException unavailableInPublishedVersion) {
      context.addTransactionListener(Name.of("probeClose"), listener);
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static Object attempt(Parser parser, String source, long deadline) throws Exception {
    Method method = ClassicP4PreferredAstMapper.class.getDeclaredMethod(
        "parseWithRoot", Parser.class, String.class, long.class);
    return invoke(method, parser, source, deadline);
  }

  private static Object attemptDetailed(Parser parser, String source) throws Exception {
    Method method = ClassicP4PreferredAstMapper.class.getDeclaredMethod(
        "parseWithRoot", Parser.class, String.class, long.class, Object.class);
    return invoke(method, parser, source, 0L, compat() == null ? null : options(false));
  }

  private static Object invoke(Method method, Object... args) throws Exception {
    method.setAccessible(true);
    try { return method.invoke(null, args); } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception cause) throw cause;
      throw (Error) e.getCause();
    }
  }

  private static Object field(Object record, String name) throws Exception {
    Method method = record.getClass().getMethod(name);
    method.setAccessible(true);
    return method.invoke(record);
  }
}
