package org.unlaxer.tinyexpression.p4;

import java.util.ArrayList;
import java.util.List;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.dsl.runtime.ScopeStore;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/**
 * Selects a more specific generated AST root when the generic mapper would
 * otherwise settle on a shallow wrapper such as {@code ExpressionExpr}.
 */
public final class P4PreferredAstMapper {

  private P4PreferredAstMapper() {}

  public record ParsedAst(TinyExpressionP4AST ast, String selectionMode) {}

  public static TinyExpressionP4AST parse(String formula) {
    return parseDetailed(formula, null).ast();
  }

  public static TinyExpressionP4AST parse(String formula, ExpressionType preferredResultType) {
    return parseDetailed(formula, preferredResultType).ast();
  }

  /**
   * Parses {@code formula} requesting a specific AST node type by its simple class name.
   * Unlike {@link #parse(String, ExpressionType)}, this method forwards the simple name
   * directly to the underlying mapper so that it is honoured even when the result type is
   * not known.  {@link ScopeStore#registerDispatcher(org.unlaxer.context.ParseContext)} is
   * called on the {@link org.unlaxer.context.ParseContext} before parsing, preventing
   * "transaction nest is illegal" errors that occur when the dispatcher is absent.
   *
   * @param formula               the expression source
   * @param preferredAstSimpleName the simple class name of the desired AST node, or {@code null}
   * @return the parsed AST
   * @throws IllegalArgumentException if the formula cannot be parsed
   */
  public static TinyExpressionP4AST parseByAstSimpleName(String formula, String preferredAstSimpleName) {
    return parseViaMapperCompat(
        formula == null ? "" : formula, preferredAstSimpleName, defaultParseDeadlineNanos());
  }

  /**
   * P4 パースのデフォルト期限。生成 P4 文法は深いネストで指数バックトラックに
   * なり得るため (issue #19, #20)、全エントリポイントに既定で適用する。正常な式は
   * ms オーダーで終わるため 10 秒は実質無害。
   * {@code -Dtinyexpression.p4.parse.timeout.millis} で調整 (0 以下で無効)。
   */
  private static long defaultParseDeadlineNanos() {
    long timeoutMillis = Long.getLong("tinyexpression.p4.parse.timeout.millis", 10_000L);
    if (timeoutMillis <= 0L) {
      return 0L;
    }
    return System.nanoTime() + timeoutMillis * 1_000_000L;
  }

  /**
   * {@link #parseByAstSimpleName(String, String)} の期限付き版。
   *
   * <p>深くネストした式では生成 P4 文法のバックトラックが指数的になり、パースが
   * 実質終了しないことがある (issue #19)。{@code deadlineNanos}
   * ({@link System#nanoTime()} 基準の絶対時刻) を過ぎるとパースを
   * {@link ParseDeadlineExceededException} で中断する。0 以下なら無期限。
   *
   * <p>実装はスレッドを使わない: 全パーサーのトランザクション begin で呼ばれる
   * {@link org.unlaxer.listener.TransactionListener} を {@link ParseContext} に
   * 登録し、期限超過時に throw してパースループを同一スレッドで巻き戻す。
   */
  public static TinyExpressionP4AST parseByAstSimpleName(
      String formula, String preferredAstSimpleName, long deadlineNanos) {
    return parseViaMapperCompat(formula == null ? "" : formula, preferredAstSimpleName, deadlineNanos);
  }

  /**
   * Parses once and selects the first exact, whole-source AST root from {@code candidates}.
   * Candidate order remains type-driven; importantly, candidates do not cause repeated parsing.
   */
  public static TinyExpressionP4AST parseByAstSimpleNames(
      String formula, List<String> candidates, long deadlineNanos) {
    String source = formula == null ? "" : formula;
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("No generated AST candidates supplied");
    }
    return parseMappedCandidates(source, candidates, false, deadlineNanos).ast();
  }

  /** パース期限超過。生成 P4 専用バックエンドでは明示的なパース失敗として扱う。 */
  public static final class ParseDeadlineExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ParseDeadlineExceededException(String message) {
      super(message);
    }
  }

  public static ParsedAst parseDetailed(String formula) {
    return parseDetailed(formula, null);
  }

  public static ParsedAst parseDetailed(String formula, ExpressionType preferredResultType) {
    String source = formula == null ? "" : formula;
    List<String> candidates = preferredAstSimpleNames(source, preferredResultType);
    return parseMappedCandidates(
        source, candidates, preferredResultType == null, defaultParseDeadlineNanos());
  }

  /**
   * Compatibility entry point retained for callers compiled against older releases.
   * Generated parsers now consume expression snippets directly; no source rewriting is applied.
   */
  public static String normalizeExpressionSnippetForParsing(String formula) {
    return formula == null ? "" : formula;
  }

  /**
   * Compatibility entry point retained after removal of the parenthesized-slice rewrite.
   */
  public static String normalizeParenthesizedSliceReceivers(String formula) {
    return formula;
  }

  public static List<String> preferredAstSimpleNames(String formula) {
    return preferredAstSimpleNames(formula, null);
  }

  /**
   * Returns deterministic generated-AST candidates. Candidate selection depends only on
   * the requested result type, never on hand-scanning the source text.
   */
  public static List<String> preferredAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    if (formula == null || formula.isBlank()) {
      return List.of();
    }
    return candidateAstSimpleNames(preferredResultType, CandidateProfile.PREFERRED).stream()
        .filter(candidate -> candidate != null)
        .toList();
  }

  public static List<String> astEvaluatorCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return formula == null || formula.isBlank()
        ? List.of()
        : candidateAstSimpleNames(preferredResultType, CandidateProfile.AST_EVALUATOR);
  }

  public static List<String> generatedValueCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return formula == null || formula.isBlank()
        ? List.of()
        : candidateAstSimpleNames(preferredResultType, CandidateProfile.GENERATED_VALUE);
  }

  public static List<String> declarationCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return formula == null || formula.isBlank()
        ? List.of()
        : candidateAstSimpleNames(preferredResultType, CandidateProfile.DECLARATION);
  }

  private static List<String> candidateAstSimpleNames(
      ExpressionType resultType, CandidateProfile profile) {
    ArrayList<String> names = new ArrayList<>();
    addIfAbsent(names, preferredMatchAstSimpleName(resultType));
    addIfAbsent(names, "IfExpr");
    addIfAbsent(names, "TernaryExpr");
    addIfAbsent(names, externalInvocationAstSimpleName(resultType));
    if (resultType == null) {
      addIfAbsent(names, "NumberMatchExpr");
      addIfAbsent(names, "StringMatchExpr");
      addIfAbsent(names, "BooleanMatchExpr");
    }

    for (String structured : List.of(
        "SinExpr", "CosExpr", "TanExpr", "SqrtExpr", "MinExpr", "MaxExpr",
        "RandomExpr", "AbsExpr", "RoundExpr", "CeilExpr", "FloorExpr", "PowExpr",
        "LogExpr", "ExpExpr", "ToNumExpr", "ToUpperCaseExpr", "ToLowerCaseExpr",
        "TrimExpr", "LengthExpr", "ToUpperCaseDotExpr", "ToLowerCaseDotExpr",
        "TrimDotExpr", "LengthDotExpr", "StartsWithExpr", "EndsWithExpr",
        "ContainsExpr", "InExpr", "StartsWithDotExpr", "EndsWithDotExpr",
        "ContainsDotExpr", "IsPresentExpr", "InTimeRangeExpr", "InDayTimeRangeExpr",
        "SliceExpr", "MethodInvocationExpr")) {
      addIfAbsent(names, structured);
    }

    if (resultType != null && resultType.isString()) {
      addIfAbsent(names, "StringConcatExpr");
    } else if (resultType != null && resultType.isBoolean()) {
      addIfAbsent(names,
          profile == CandidateProfile.DECLARATION ? "BooleanExpr" : "BooleanOrExpr");
    } else if (resultType != null && resultType.isObject()) {
      addIfAbsent(names, "ObjectExpr");
      addIfAbsent(names, "StringConcatExpr");
      addIfAbsent(names, "BooleanOrExpr");
      addIfAbsent(names, "BinaryExpr");
    } else {
      addIfAbsent(names, "BinaryExpr");
    }

    addIfAbsent(names, "VariableRefExpr");
    addIfAbsent(names, "FormulaExpr");
    return names;
  }

  private static String externalInvocationAstSimpleName(ExpressionType resultType) {
    if (resultType == null) return null;
    if (resultType.isBoolean()) return "ExternalBooleanInvocationExpr";
    if (resultType.isNumber()) return "ExternalNumberInvocationExpr";
    if (resultType.isString()) return "ExternalStringInvocationExpr";
    if (resultType.isObject()) return "ExternalObjectInvocationExpr";
    return null;
  }

  private static String preferredMatchAstSimpleName(ExpressionType resultType) {
    if (resultType == null) return null;
    if (resultType.isString()) return "StringMatchExpr";
    if (resultType.isBoolean()) return "BooleanMatchExpr";
    if (resultType.isNumber()) return "NumberMatchExpr";
    return null;
  }

  private static void addIfAbsent(List<String> names, String candidate) {
    if (candidate != null && !candidate.isBlank() && !names.contains(candidate)) {
      names.add(candidate);
    }
  }

  private enum CandidateProfile {
    PREFERRED,
    AST_EVALUATOR,
    GENERATED_VALUE,
    DECLARATION
  }

  private static IllegalArgumentException toParseFailure(RuntimeException failure) {
    if (failure instanceof IllegalArgumentException illegalArgumentException) {
      return illegalArgumentException;
    }
    return new IllegalArgumentException(failure.getMessage(), failure);
  }

  private static TinyExpressionP4AST parseViaMapperCompat(String source, String preferredAstSimpleName) {
    return parseViaMapperCompat(source, preferredAstSimpleName, defaultParseDeadlineNanos());
  }

  private static TinyExpressionP4AST parseViaMapperCompat(
      String source, String preferredAstSimpleName, long deadlineNanos) {
    return parseMappedCandidates(
        source,
        preferredAstSimpleName == null ? List.of() : List.of(preferredAstSimpleName),
        preferredAstSimpleName == null,
        deadlineNanos).ast();
  }

  private static ParsedAst parseMappedCandidates(
      String source, List<String> candidates, boolean allowDefault, long deadlineNanos) {
    Token rootToken = parseRootToken(source, deadlineNanos);
    // Comments are consumed by the 3.0.15 grammar but are not part of the mapped
    // node's token text. Normalize only for the selection check; the parser still
    // receives the original source, preserving source spans and block comments.
    String sourceWithoutComments = TinyExpressionParserCapabilities
        .stripJavaStyleCommentsPreservingLayout(source);
    RuntimeException lastFailure = null;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      try {
        TinyExpressionP4Mapper.MappedAst mappedAst =
            TinyExpressionP4Mapper.mapParsedToken(rootToken, candidate);
        if (!coversWholeSource(sourceWithoutComments, mappedAst.token())) {
          continue;
        }
        TinyExpressionP4AST mapped = mappedAst.ast();
        if (mapped != null && candidate.equals(mapped.getClass().getSimpleName())) {
          return new ParsedAst(mapped, "preferred:" + candidate);
        }
      } catch (RuntimeException failure) {
        lastFailure = failure;
      }
    }
    if (allowDefault) {
      TinyExpressionP4AST mapped = TinyExpressionP4Mapper.mapParsedToken(rootToken).ast();
      if (mapped != null) {
        return new ParsedAst(mapped, "default");
      }
    }
    if (lastFailure != null) {
      throw toParseFailure(lastFailure);
    }
    throw new IllegalArgumentException("No whole-source generated AST mapping found: " + source);
  }

  private static boolean coversWholeSource(String source, Token token) {
    String mappedSource = tokenTextCompat(token);
    return mappedSource != null && source.strip().equals(mappedSource.strip());
  }

  /**
   * Parses {@code source} fully and returns the root token.
   *
   * <p>The grammar's top-level {@code Expression} rule tries {@code NumberExpression}
   * before {@code BooleanExpression} so that arithmetic such as {@code $a+$b} is fully
   * consumed (a BooleanExpression-first ordering would consume only {@code $a}). The
   * generated combinator {@code Choice} is PEG first-match and never backtracks into a
   * later alternative once an earlier one commits, so for a bare top-level boolean
   * comparison such as {@code 1 > 0 & 2 > 1} the {@code NumberExpression} alternative
   * matches only the leading {@code 1} and the root {@code Formula} rule then fails at
   * EOF (issue #23).
   *
   * <p>Reordering the grammar alternatives cannot fix both cases under PEG, and adding a
   * comparison-anchored top-level alternative roughly doubles parse time for
   * {@code if(...comparison...)} formulas (the comparison's number operand re-parses the
   * whole {@code if} block before failing). So the fix is applied here instead: when the
   * standard {@code Formula} parse fails or under-consumes, retry once treating
   * {@code BooleanExpression} as the root. This adds cost only for inputs the standard
   * parse already rejected, leaving the hot path untouched.
   */
  private static Token parseRootToken(String source, long deadlineNanos) {
    ParseResult primary = parseWithRoot(TinyExpressionP4Parsers.getRootParser(), source, deadlineNanos);
    if (primary.fullyConsumed(source)) {
      return primary.rootToken();
    }
    // issue #23 compatibility retry: a bare top-level boolean comparison can be shadowed by
    // top-level expression dispatch. Retry with BooleanExpression as the generated root.
    ParseResult booleanRoot = parseWithRoot(
        Parser.get(TinyExpressionP4Parsers.BooleanExpressionParser.class), source, deadlineNanos);
    if (booleanRoot.fullyConsumed(source)) {
      return booleanRoot.rootToken();
    }
    if (!primary.succeeded()) {
      throw new IllegalArgumentException("Parse failed: " + source);
    }
    throw new IllegalArgumentException("Parse failed at offset " + primary.consumed() + ": " + source);
  }

  /**
   * Packrat memoization is ON by default (see {@link #parseWithRoot}); opt out with
   * {@code -Dtinyexpression.p4.memoize=false}.
   */
  private static boolean memoizeEnabled() {
    return false == "false".equalsIgnoreCase(System.getProperty("tinyexpression.p4.memoize", "true"));
  }

  private static ParseResult parseWithRoot(Parser rootParser, String source, long deadlineNanos) {
    ParseContext context = new ParseContext(createRootSourceCompat(source));
    ScopeStore.registerDispatcher(context);
    // Packrat memoization (unlaxer-parser #40): collapses the exponential backtracking that deeply
    // nested fraud-detection formulas trigger (#19/#38). ON by default now that it is proven fast and
    // parse-equivalent (parity verified in #40) and that the mapping phase no longer re-maps subtrees
    // (tinyexpression #49) — together these let formulas that previously blew the parse deadline (e.g.
    // toUpperCase('..')[4:6].in(..), the giant nested-if fraud formulas) stay on the P4 path instead of
    // failing explicitly. Opt OUT with -Dtinyexpression.p4.memoize=false. Safe with the
    // @scopeTree/@declares/@backref grammar because memoization excludes TransactionListener-bearing
    // sub-trees (scope effects are never skipped).
    if (memoizeEnabled()) {
      try {
        context.enableMemoize();
      } catch (NoSuchMethodError _e) {
        // unlaxer-common の版が enableMemoize() を持たない（Central の 3.0.11 が
        // 旧版のまま publish されている等）。memoize は性能最適化で必須ではない —
        // 深くネストした式で遅くなるが、機能はする。issue #67 参照。
      }
    }
    if (deadlineNanos > 0L) {
      registerDeadlineListener(context, deadlineNanos);
    }
    Parsed parsed;
    try {
      parsed = rootParser.parse(context);
    } finally {
      closeParseContextQuietly(context);
    }
    if (!parsed.isSucceeded()) {
      return new ParseResult(false, -1, null);
    }
    int consumed = consumedLengthCompat(parsed.getConsumed());
    return new ParseResult(true, consumed, parsed.getRootToken(true));
  }

  private record ParseResult(boolean succeeded, int consumed, Token rootToken) {
    boolean fullyConsumed(String source) {
      return succeeded && consumed == source.length();
    }
  }

  /**
   * 期限超過でパースを中断する listener を登録する。unlaxer 3.0.4 以降、
   * TransactionListenerContainer は登録 listener の onBegin を全パーサーの
   * トランザクション begin で呼ぶため、ここで throw すればパースループが
   * 同一スレッドで巻き戻る (スレッド・割り込み不要)。
   */
  private static void registerDeadlineListener(ParseContext context, long deadlineNanos) {
    context.addTransactionListener(
        org.unlaxer.Name.of(P4PreferredAstMapper.class, "parseDeadline"),
        new org.unlaxer.listener.TransactionListener() {
          @Override public void setLevel(org.unlaxer.listener.OutputLevel level) {}
          @Override public void onOpen(ParseContext parseContext) {}
          @Override public void onBegin(ParseContext parseContext, Parser parser) {
            if (System.nanoTime() > deadlineNanos) {
              throw new ParseDeadlineExceededException("P4 parse exceeded deadline");
            }
          }
          @Override public void onCommit(ParseContext parseContext, Parser parser, org.unlaxer.TokenList committedTokens) {}
          @Override public void onRollback(ParseContext parseContext, Parser parser, org.unlaxer.TokenList rollbackedTokens) {}
          @Override public void onClose(ParseContext parseContext) {}
        });
  }

  private static void closeParseContextQuietly(ParseContext context) {
    try {
      context.close();
    } catch (IllegalStateException ignored) {
      // Generated mapper parse can leave nested transactions behind for some
      // formulas even when the root token is still usable.
    }
  }

  private static int consumedLengthCompat(Token token) {
    String text = tokenTextCompat(token);
    return text == null ? 0 : text.length();
  }

  private static String tokenTextCompat(Token token) {
    if (token == null) {
      return null;
    }
    return token.getToken().orElse(null);
  }

  private static StringSource createRootSourceCompat(String source) {
    return StringSource.createRootSource(source);
  }

  private record MatchBody(String body, int bodyStartOffset) {}

  private record Segment(String text, int startOffset, int endOffset) {}
}
