package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.DiagnosticsAgnostic;
import org.unlaxer.context.DiagnosticsSafety;
import org.unlaxer.context.Memoization;
import org.unlaxer.context.ParseContext;
import org.unlaxer.context.ParseOptions;
import org.unlaxer.context.ParseOptions.Diagnostics;
import org.unlaxer.dsl.runtime.ScopeStore;
import org.unlaxer.listener.TransactionListener;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;

public class P4PreferredAstMapperDiagnosticsTest {
  private static final String MEMOIZE = "tinyexpression.p4.memoize";

  private static List<Parser> roots() {
    return List.of(TinyExpressionP4Parsers.getRootParser(),
        Parser.get(TinyExpressionP4Parsers.BooleanExpressionParser.class),
        Parser.get(TinyExpressionP4Parsers.StringExpressionParser.class),
        Parser.get(TinyExpressionP4Parsers.ObjectExpressionParser.class));
  }

  @Test public void allRootGraphsAreDeferredDiagnosticsSafe() {
    Set<Parser> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    var pending = new ArrayDeque<>(roots());
    while (!pending.isEmpty()) {
      Parser parser = pending.pop();
      if (!visited.add(parser)) continue;
      assertTrue(parser.getClass().getName(), DiagnosticsSafety.isDeferredDiagnosticsSafe(parser));
      // tinyexpression's shadow AbstractParser does not implement HasChildrenParser. Walk its
      // children too, so custom tokens inside constructed repetitions cannot escape this check.
      pending.addAll(parser.getChildren());
    }
  }

  @Test public void successUsesOneDeferredContextWithEitherMemoizationPolicy() throws Exception {
    String previous = System.getProperty(MEMOIZE);
    try {
      for (boolean memoize : List.of(true, false)) {
        System.setProperty(MEMOIZE, Boolean.toString(memoize));
        var parser = new SafeProbe();
        Object result = attempt(parser, "ok", 0L);
        assertEquals(true, field(result, "succeeded"));
        assertEquals(2, field(result, "consumed"));
        assertEquals(1, parser.contexts.size());
        assertEquals(Diagnostics.DETAILED_ON_FAILURE, parser.contexts.getFirst().getOptions().diagnostics());
        assertEquals(memoize ? Memoization.SAFE_FAILURES : Memoization.OFF,
            parser.contexts.getFirst().getOptions().memoization());
        assertEquals(1, parser.closed);
      }
    } finally {
      if (previous == null) System.clearProperty(MEMOIZE); else System.setProperty(MEMOIZE, previous);
    }
  }

  @Test public void syntaxFailureAndTrailingInputRetryWithFreshDetailedState() throws Exception {
    for (String source : List.of("bad", "ok😀")) {
      var parser = new SafeProbe();
      Object result = attempt(parser, source, System.nanoTime() + 5_000_000_000L);
      assertEquals(source.startsWith("ok"), field(result, "succeeded"));
      assertEquals(source.startsWith("ok") ? 2 : -1, field(result, "consumed"));
      assertEquals(2, parser.contexts.size());
      ParseContext first = parser.contexts.get(0), retry = parser.contexts.get(1);
      assertNotSame(first, retry);
      assertEquals(Diagnostics.DETAILED_ON_FAILURE, first.getOptions().diagnostics());
      assertEquals(Diagnostics.DETAILED, retry.getOptions().diagnostics());
      assertEquals(first.getOptions().memoization(), retry.getOptions().memoization());
      if (first.isMemoizeEnabled()) assertNotSame(first.getPackratMemoTable(), retry.getPackratMemoTable());
      assertEquals(2, parser.closed);
      assertEquals(List.of(true, true), parser.deadlineRegistered);
      if (source.equals("bad")) {
        assertTrue(first.getParseFailureDiagnostics().getExpectedTokens().isEmpty());
        assertFalse(retry.getParseFailureDiagnostics().getExpectedTokens().isEmpty());
      }
    }
  }

  @Test public void undeclaredParserKeepsDetailedDiagnosticsWithoutRetry() throws Exception {
    var parser = new Probe();
    attempt(parser, "bad", 0L);
    assertEquals(1, parser.contexts.size());
    assertEquals(Diagnostics.DETAILED, parser.contexts.getFirst().getOptions().diagnostics());
    assertFalse(parser.contexts.getFirst().getParseFailureDiagnostics().getExpectedTokens().isEmpty());
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

  @Test public void generatedDiagnosticsAndExceptionMessagesMatchDetailedPolicy() {
    ParseOptions options = ParseOptions.withMemoization(Memoization.SAFE_FAILURES);
    for (String source : List.of("(1 +", "'😀' @", "var $n as number set 3;\n$n +", "")) {
      // Record equality checks every ParseDiagnostic field, including both expected lists.
      var detailed = options.withDiagnostics(Diagnostics.DETAILED);
      assertEquals(TinyExpressionP4Mapper.diagnose(source, detailed),
          TinyExpressionP4Mapper.diagnose(source, options));
      assertEquals(assertThrows(IllegalArgumentException.class,
          () -> TinyExpressionP4Mapper.parseWithOptions(source, detailed)).getMessage(),
          assertThrows(IllegalArgumentException.class,
              () -> TinyExpressionP4Mapper.parseWithOptions(source, options)).getMessage());
      assertEquals("Parse failed: " + source, assertThrows(IllegalArgumentException.class,
          () -> P4PreferredAstMapper.parseDetailed(source)).getMessage());
    }
  }

  @Test public void expiredDeadlineAbortsFirstPassWithoutRetry() {
    var parser = new SafeProbe();
    assertThrows(P4PreferredAstMapper.ParseDeadlineExceededException.class,
        () -> attempt(parser, "bad", System.nanoTime() - 1L));
    assertEquals(1, parser.contexts.size());
  }

  @Test public void retryDeadlineListenerStillAbortsParsing() {
    var parser = new SafeProbe();
    parser.expireRetry = true;
    assertThrows(P4PreferredAstMapper.ParseDeadlineExceededException.class,
        () -> attempt(parser, "bad", System.nanoTime() + 150_000_000L));
    assertEquals(2, parser.contexts.size());
  }

  @Test public void retryReceivesTheOriginalRemainingTimeBudget() throws Exception {
    var parser = new SafeProbe();
    parser.pauseMillis = 600L;
    long deadline = System.nanoTime() + 1_000_000_000L;
    Object result = attempt(parser, "bad", deadline);
    assertEquals(false, field(result, "succeeded"));
    assertEquals(2, parser.contexts.size());
    assertEquals(2, parser.closed);
    assertTrue("two passes together exceed the initial deadline", System.nanoTime() > deadline);
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
          Name.of(P4PreferredAstMapper.class, "parseDeadline")));
      context.addMemoizationTransparentTransactionListener(Name.of("probeClose"), new TransactionListener() {
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

  private static final class SafeProbe extends Probe implements DiagnosticsAgnostic {}

  private static Object attempt(Parser parser, String source, long deadline) throws Exception {
    Method method = P4PreferredAstMapper.class.getDeclaredMethod(
        "parseWithRoot", Parser.class, String.class, long.class);
    return invoke(method, parser, source, deadline);
  }

  private static Object attemptDetailed(Parser parser, String source) throws Exception {
    Method method = P4PreferredAstMapper.class.getDeclaredMethod(
        "parseWithRoot", Parser.class, String.class, long.class, ParseOptions.class);
    return invoke(method, parser, source, 0L,
        ParseOptions.withMemoization(Memoization.SAFE_FAILURES).withDiagnostics(Diagnostics.DETAILED));
  }

  private static Object invoke(Method method, Object... args) throws Exception {
    method.setAccessible(true);
    try { return method.invoke(null, args); } catch (InvocationTargetException e) {
      if (e.getCause() instanceof Exception cause) throw cause;
      throw (Error) e.getCause();
    }
  }

  private static Object field(Object record, String name) throws Exception {
    Method method = record.getClass().getDeclaredMethod(name);
    method.setAccessible(true);
    return method.invoke(record);
  }
}
