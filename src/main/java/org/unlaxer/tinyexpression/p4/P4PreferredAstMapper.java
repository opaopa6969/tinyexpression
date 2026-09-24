package org.unlaxer.tinyexpression.p4;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.ubnfc.UbnfcP4Parse;
import org.unlaxer.tinyexpression.parser.ExpressionType;

/**
 * Parses a TinyExpression P4 formula into the generated typed AST and selects a more specific
 * root when the generic mapping would otherwise settle on a shallow wrapper such as
 * {@code ExpressionExpr}.
 *
 * <p>Since 2.0.0 the implementation is chosen by {@link P4ParserEngine}: the ubnfc-generated
 * parser by default, or the pre-2.0 combinator path with {@code legacy}. Both return the same
 * {@link ParsedAst} (same {@link TinyExpressionP4AST} records, same {@code selectionMode}
 * strings, code-point {@link P4SourceText} spans) and fail with the same exception types and
 * messages; {@code UbnfcParityTest} pins this over every formula the test suite feeds here.
 *
 * <p>Differences that are intentional:
 * <ul>
 *   <li>{@code tinyexpression.p4.parse.timeout.millis}: the ubnfc parser is packrat with a depth
 *       limit and cannot backtrack exponentially (issues #19, #20), so the deadline is checked
 *       before and after parsing only. {@link ParseDeadlineExceededException} remains.</li>
 *   <li>{@code tinyexpression.p4.memoize} only affects {@code legacy} (ubnfc is always packrat).</li>
 * </ul>
 */
public final class P4PreferredAstMapper {

  private P4PreferredAstMapper() {}

  public record ParsedAst(TinyExpressionP4AST ast, String selectionMode, P4SourceText sourceText) {
    public ParsedAst {
      Objects.requireNonNull(sourceText, "sourceText");
    }

    public ParsedAst(TinyExpressionP4AST ast, String selectionMode) {
      this(ast, selectionMode, P4SourceText.lexicalOnly());
    }
  }

  /** パース期限超過。生成 P4 専用バックエンドでは明示的なパース失敗として扱う。 */
  public static final class ParseDeadlineExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    ParseDeadlineExceededException(String message) {
      super(message);
    }
  }

  /** The engine the next call on this thread will use. */
  public static P4ParserEngine engine() {
    return P4ParserEngine.current();
  }

  public static TinyExpressionP4AST parse(String formula) {
    return parseDetailed(formula, null).ast();
  }

  public static TinyExpressionP4AST parse(String formula, ExpressionType preferredResultType) {
    return parseDetailed(formula, preferredResultType).ast();
  }

  /**
   * Parses {@code formula} requesting a specific AST node type by its simple class name, with the
   * default deadline ({@code -Dtinyexpression.p4.parse.timeout.millis}, 10 s; 0 or less disables).
   *
   * @throws IllegalArgumentException if the formula cannot be parsed
   */
  public static TinyExpressionP4AST parseByAstSimpleName(String formula, String preferredAstSimpleName) {
    return parseByAstSimpleNameDetailed(
        formula, preferredAstSimpleName, LegacyP4PreferredAstMapper.defaultParseDeadlineNanos()).ast();
  }

  /**
   * Deadline-bounded variant. {@code deadlineNanos} is an absolute {@link System#nanoTime()}
   * instant; 0 or less means no deadline. Exceeding it raises {@link ParseDeadlineExceededException}.
   */
  public static TinyExpressionP4AST parseByAstSimpleName(
      String formula, String preferredAstSimpleName, long deadlineNanos) {
    return parseByAstSimpleNameDetailed(formula, preferredAstSimpleName, deadlineNanos).ast();
  }

  /** Exact single-candidate mapping with its immutable owned source snapshot. */
  public static ParsedAst parseByAstSimpleNameDetailed(
      String formula, String preferredAstSimpleName, long deadlineNanos) {
    if (engine() == P4ParserEngine.LEGACY) {
      return LegacyP4PreferredAstMapper.parseByAstSimpleNameDetailed(
          formula, preferredAstSimpleName, deadlineNanos);
    }
    return ubnfc(() -> UbnfcP4Parse.parseByAstSimpleNameDetailed(
        formula, preferredAstSimpleName, deadlineNanos));
  }

  /**
   * Parses once and selects the first exact, whole-source AST root from {@code candidates}.
   * Candidate order remains type-driven; candidates do not cause repeated parsing.
   */
  public static TinyExpressionP4AST parseByAstSimpleNames(
      String formula, List<String> candidates, long deadlineNanos) {
    return parseByAstSimpleNamesDetailed(formula, candidates, deadlineNanos).ast();
  }

  /** Retains the selected AST's owned source resolver for delayed evaluation. */
  public static ParsedAst parseByAstSimpleNamesDetailed(
      String formula, List<String> candidates, long deadlineNanos) {
    if (engine() == P4ParserEngine.LEGACY) {
      return LegacyP4PreferredAstMapper.parseByAstSimpleNamesDetailed(
          formula, candidates, deadlineNanos);
    }
    return ubnfc(() -> UbnfcP4Parse.parseByAstSimpleNamesDetailed(formula, candidates, deadlineNanos));
  }

  public static ParsedAst parseDetailed(String formula) {
    return parseDetailed(formula, null);
  }

  public static ParsedAst parseDetailed(String formula, ExpressionType preferredResultType) {
    if (engine() == P4ParserEngine.LEGACY) {
      return LegacyP4PreferredAstMapper.parseDetailed(formula, preferredResultType);
    }
    return ubnfc(() -> UbnfcP4Parse.parseDetailed(formula, preferredResultType));
  }

  /**
   * Compatibility entry point retained for callers compiled against older releases.
   * Generated parsers consume expression snippets directly; no source rewriting is applied.
   */
  public static String normalizeExpressionSnippetForParsing(String formula) {
    return formula == null ? "" : formula;
  }

  /** Compatibility entry point retained after removal of the parenthesized-slice rewrite. */
  public static String normalizeParenthesizedSliceReceivers(String formula) {
    return formula;
  }

  public static List<String> preferredAstSimpleNames(String formula) {
    return preferredAstSimpleNames(formula, null);
  }

  /**
   * Returns deterministic generated-AST candidates. Candidate selection depends only on the
   * requested result type, never on hand-scanning the source text.
   */
  public static List<String> preferredAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return engine() == P4ParserEngine.LEGACY
        ? LegacyP4PreferredAstMapper.preferredAstSimpleNames(formula, preferredResultType)
        : UbnfcP4Parse.preferredAstSimpleNames(formula, preferredResultType);
  }

  public static List<String> astEvaluatorCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return engine() == P4ParserEngine.LEGACY
        ? LegacyP4PreferredAstMapper.astEvaluatorCandidateAstSimpleNames(formula, preferredResultType)
        : UbnfcP4Parse.astEvaluatorCandidateAstSimpleNames(formula, preferredResultType);
  }

  public static List<String> generatedValueCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return engine() == P4ParserEngine.LEGACY
        ? LegacyP4PreferredAstMapper.generatedValueCandidateAstSimpleNames(formula, preferredResultType)
        : UbnfcP4Parse.generatedValueCandidateAstSimpleNames(formula, preferredResultType);
  }

  public static List<String> declarationCandidateAstSimpleNames(
      String formula, ExpressionType preferredResultType) {
    return engine() == P4ParserEngine.LEGACY
        ? LegacyP4PreferredAstMapper.declarationCandidateAstSimpleNames(formula, preferredResultType)
        : UbnfcP4Parse.declarationCandidateAstSimpleNames(formula, preferredResultType);
  }

  /** Wraps the engine-neutral result and re-throws a deadline miss as this class's type. */
  private static ParsedAst ubnfc(Supplier<UbnfcP4Parse.Result> parse) {
    UbnfcP4Parse.Result result;
    try {
      result = parse.get();
    } catch (UbnfcP4Parse.ParseDeadlineExceededException exceeded) {
      throw new ParseDeadlineExceededException(exceeded.getMessage());
    }
    return new ParsedAst(result.ast(), result.selectionMode(), result.sourceText());
  }
}
