package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unlaxer.Parsed;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.context.ParseContextEffector;
import org.unlaxer.dsl.runtime.ScopeStore;
import org.unlaxer.parser.HasChildrenParser;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.StringLiteralParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;
import org.unlaxer.tinyexpression.parser.javalang.CodeEndParser;
import org.unlaxer.tinyexpression.parser.javalang.CodeStartParser;
import org.unlaxer.tinyexpression.parser.javalang.TripleBackTickParser;
import org.unlaxer.tinyexpression.parser.javatype.JavaClassNameParser;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper.ParseDeadlineExceededException;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper.ParsedAst;

/**
 * The pre-2.0 (combinator) implementation behind {@link P4PreferredAstMapper}: the
 * unlaxer-dsl generated {@code TinyExpressionP4Parsers} + {@code TinyExpressionP4Mapper}.
 * Selected with {@link P4ParserEngine#CLASSIC} (unlaxer Classic; deprecated alias
 * {@code legacy}); retained through 2.x and scheduled for removal in 3.0. The public surface
 * lives on {@link P4PreferredAstMapper}.
 *
 * <p>Selects a more specific generated AST root when the generic mapper would
 * otherwise settle on a shallow wrapper such as {@code ExpressionExpr}.
 */
final class ClassicP4PreferredAstMapper {

  // Generated root graphs are fixed after preparation. Check each root only once, by identity.
  private static final Map<Parser, Boolean> DEFERRED_DIAGNOSTICS_SAFE =
      Collections.synchronizedMap(new IdentityHashMap<>());

  // These exact classes neither read diagnostics while parsing nor perform non-repeatable effects.
  // Do not accept subclasses implicitly: their overrides have not been audited.
  private static final Set<Class<? extends Parser>> DEFERRED_SAFE_CUSTOM_PARSERS = Set.of(
      StringLiteralParser.class, CodeStartParser.class, CodeEndParser.class,
      TripleBackTickParser.class, JavaClassNameParser.class);

  // Published 3.0.15 lacks the options API. Resolve the entire capability once, without linking it.
  private static final DiagnosticsCompat DIAGNOSTICS_COMPAT = DiagnosticsCompat.resolve();

  private ClassicP4PreferredAstMapper() {}

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
    return parseByAstSimpleNameDetailed(
        formula, preferredAstSimpleName, defaultParseDeadlineNanos()).ast();
  }

  /**
   * P4 パースのデフォルト期限。生成 P4 文法は深いネストで指数バックトラックに
   * なり得るため (issue #19, #20)、全エントリポイントに既定で適用する。正常な式は
   * ms オーダーで終わるため 10 秒は実質無害。
   * {@code -Dtinyexpression.p4.parse.timeout.millis} で調整 (0 以下で無効)。
   */
  static long defaultParseDeadlineNanos() {
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
   * 構文失敗時の詳細診断用再解析には、初回開始時の残り予算をもう一度適用する。
   *
   * <p>実装はスレッドを使わない: 全パーサーのトランザクション begin で呼ばれる
   * {@link org.unlaxer.listener.TransactionListener} を {@link ParseContext} に
   * 登録し、期限超過時に throw してパースループを同一スレッドで巻き戻す。
   */
  public static TinyExpressionP4AST parseByAstSimpleName(
      String formula, String preferredAstSimpleName, long deadlineNanos) {
    return parseByAstSimpleNameDetailed(formula, preferredAstSimpleName, deadlineNanos).ast();
  }

  /** Exact single-candidate mapping with its immutable owned source snapshot. */
  public static ParsedAst parseByAstSimpleNameDetailed(
      String formula, String preferredAstSimpleName, long deadlineNanos) {
    String source = formula == null ? "" : formula;
    return parseMappedCandidates(
        source,
        preferredAstSimpleName == null ? List.of() : List.of(preferredAstSimpleName),
        preferredAstSimpleName == null,
        deadlineNanos);
  }

  /**
   * Parses once and selects the first exact, whole-source AST root from {@code candidates}.
   * Candidate order remains type-driven; importantly, candidates do not cause repeated parsing.
   */
  public static TinyExpressionP4AST parseByAstSimpleNames(
      String formula, List<String> candidates, long deadlineNanos) {
    return parseByAstSimpleNamesDetailed(formula, candidates, deadlineNanos).ast();
  }

  /** Retains the selected AST's owned source resolver for delayed evaluation. */
  public static ParsedAst parseByAstSimpleNamesDetailed(
      String formula, List<String> candidates, long deadlineNanos) {
    String source = formula == null ? "" : formula;
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("No generated AST candidates supplied");
    }
    ParsedAst parsed = parseMappedCandidates(source, candidates, false, deadlineNanos);
    int parsedIndex = candidates.indexOf(parsed.ast().getClass().getSimpleName());
    boolean earlierTypedFamily = parsedIndex > 0 && candidates.subList(0, parsedIndex).stream()
        .anyMatch(ClassicP4PreferredAstMapper::isTypedFamilyRoot);
    if (!earlierTypedFamily) return parsed;
    ParsedAst selected = selectExplicitResultFamily(source, candidates, parsed, deadlineNanos);
    int selectedIndex = candidates.indexOf(selected.ast().getClass().getSimpleName());
    return selectedIndex >= 0 && selectedIndex <= parsedIndex ? selected : parsed;
  }

  private static boolean isTypedFamilyRoot(String candidate) {
    return switch (candidate) {
      case "StringConcatExpr", "BooleanOrExpr", "ObjectExpr",
          "NumberMatchExpr", "StringMatchExpr", "BooleanMatchExpr" -> true;
      default -> false;
    };
  }

  public static ParsedAst parseDetailed(String formula) {
    return parseDetailed(formula, null);
  }

  public static ParsedAst parseDetailed(String formula, ExpressionType preferredResultType) {
    String source = formula == null ? "" : formula;
    List<String> candidates = preferredAstSimpleNames(source, preferredResultType);
    long deadlineNanos = defaultParseDeadlineNanos();
    ParsedAst parsed = parseMappedCandidates(
        source, candidates, preferredResultType == null, deadlineNanos);
    return selectExplicitResultFamily(source, candidates, parsed, deadlineNanos);
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

  private static ParsedAst parseMappedCandidates(
      String source, List<String> candidates, boolean allowDefault, long deadlineNanos) {
    // Keep source offsets stable while accepting comments in positions where the generated
    // grammar's interleave metadata is not applied to nested alternatives.
    String parserSource = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
    ParsedRoot parsedRoot = parseRootToken(parserSource, deadlineNanos);
    return mapCandidates(parserSource, candidates, allowDefault, parsedRoot);
  }

  private static ParsedAst mapCandidates(
      String parserSource, List<String> candidates, boolean allowDefault, ParsedRoot parsedRoot) {
    Token rootToken = parsedRoot.token();
    String sourceForSpanComparison = parserSource;
    P4SourceMapping.Mapped tree;
    try {
      tree = P4SourceMapping.mapOnce(
          rootToken, parsedRoot.legacyToken(), parserSource, parsedRoot.entryPoint());
    } catch (RuntimeException failure) {
      // Preserve candidate conversion and default propagation even when mapping itself fails.
      tree = preferred -> { throw failure; };
    }
    RuntimeException lastFailure = null;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) {
        continue;
      }
      try {
        P4SourceMapping.Selection mappedAst = tree.select(candidate);
        if (!coversWholeSource(sourceForSpanComparison, mappedAst.token())) {
          continue;
        }
        TinyExpressionP4AST mapped = mappedAst.ast();
        if (mapped != null && candidate.equals(mapped.getClass().getSimpleName())) {
          return new ParsedAst(mapped, "preferred:" + candidate, mappedAst.sourceText());
        }
      } catch (RuntimeException failure) {
        lastFailure = failure;
      }
    }
    if (allowDefault) {
      P4SourceMapping.Selection selection = tree.select(null);
      TinyExpressionP4AST mapped = selection.ast();
      if (mapped != null) {
        return new ParsedAst(mapped, "default", selection.sourceText());
      }
    }
    if (lastFailure != null) {
      throw toParseFailure(lastFailure);
    }
    throw new IllegalArgumentException("No whole-source generated AST mapping found: " + parserSource);
  }

  /**
   * Uses captured {@code VariableRefExpr.type} metadata to select a typed top-level family.
   * The ordinary grammar remains permissive inside declarations and invocation arguments for
   * backward compatibility; only a direct result variable (or direct match result variables)
   * influences root-family selection.
   */
  private static ParsedAst selectExplicitResultFamily(
      String source, List<String> candidates, ParsedAst parsed, long deadlineNanos) {
    boolean document = isDocument(source, parsed);
    ResultFamily family = explicitResultFamily(parsed.ast());
    ResultFamily currentMatchFamily = matchFamily(parsed.ast());
    if (family == null || (!document && family == currentMatchFamily)
        || (currentMatchFamily == null && family == ResultFamily.NUMBER)) {
      return parsed;
    }
    if (currentMatchFamily != null && family == ResultFamily.OBJECT) {
      throw matchTypeMismatch(source);
    }
    String parserSource = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
    TinyExpressionP4AST.FormulaExpr documentRoot =
        document && parsed.ast() instanceof TinyExpressionP4AST.FormulaExpr formula ? formula : null;
    int[] documentExpressionSpan = null;
    int documentExpressionOffset = 0;
    if (documentRoot != null) {
      documentExpressionSpan = parsed.sourceText().spanOf(documentRoot.expression())
          .orElseThrow(() -> new IllegalArgumentException(
              "No owned source span for Formula expression"));
      documentExpressionOffset = documentExpressionSpan[0];
      parserSource = parserSource.substring(
          parserSource.offsetByCodePoints(0, documentExpressionSpan[0]),
          parserSource.offsetByCodePoints(0, documentExpressionSpan[1]));
    }
    Class<? extends Parser> rootClass = switch (family) {
      case STRING -> currentMatchFamily != null
          ? TinyExpressionP4Parsers.StringMatchExpressionParser.class
          : TinyExpressionP4Parsers.StringExpressionParser.class;
      case BOOLEAN -> currentMatchFamily != null
          ? TinyExpressionP4Parsers.BooleanMatchExpressionParser.class
          : TinyExpressionP4Parsers.BooleanExpressionParser.class;
      case OBJECT -> TinyExpressionP4Parsers.ObjectExpressionParser.class;
      case NUMBER -> {
        if (currentMatchFamily != null) {
          yield TinyExpressionP4Parsers.NumberMatchExpressionParser.class;
        }
        throw new IllegalStateException("number family already selected");
      }
    };
    ParseResult reparsed = parseWithRoot(Parser.get(rootClass), parserSource, deadlineNanos);
    if (!reparsed.fullyConsumed(parserSource)) {
      if (currentMatchFamily != null) throw matchTypeMismatch(source);
      throw new IllegalArgumentException(
          "Explicit result type could not be parsed as " + family.name().toLowerCase() + ": " + source);
    }
    ArrayList<String> familyCandidates = new ArrayList<>();
    addIfAbsent(familyCandidates, switch (family) {
      case STRING -> currentMatchFamily != null ? "StringMatchExpr" : "StringConcatExpr";
      case BOOLEAN -> currentMatchFamily != null ? "BooleanMatchExpr" : "BooleanOrExpr";
      case OBJECT -> "ObjectExpr";
      case NUMBER -> null;
    });
    for (String candidate : candidates) addIfAbsent(familyCandidates, candidate);
    ParsedAst selected = mapCandidates(
        parserSource,
        familyCandidates,
        true,
        new ParsedRoot(
            reparsed.rootToken(), reparsed.legacyToken(), P4SourceMapping.EntryPoint.ALTERNATE));
    if (family == ResultFamily.OBJECT) {
      if (selected.ast() instanceof TinyExpressionP4AST.ObjectExpr) {
        selected = new ParsedAst(selected.ast(), "explicit:object", selected.sourceText());
      } else {
        TinyExpressionP4AST.ObjectExpr object = new TinyExpressionP4AST.ObjectExpr(selected.ast());
        selected = new ParsedAst(object, "explicit:object",
            selected.sourceText().withWholeSourceNode(object));
      }
    }
    if (documentRoot == null) return selected;

    TinyExpressionP4AST.ExpressionExpr expression =
        new TinyExpressionP4AST.ExpressionExpr(selected.ast());
    TinyExpressionP4AST.FormulaExpr formula = new TinyExpressionP4AST.FormulaExpr(
        documentRoot.imports(), documentRoot.declarations(), expression, documentRoot.methods());
    P4SourceText sourceText = parsed.sourceText().withOverlay(
        selected.sourceText(), documentExpressionOffset,
        formula, documentRoot, expression, documentRoot.expression());
    return new ParsedAst(formula, "document:" + selected.selectionMode(), sourceText);
  }

  private static ResultFamily matchFamily(TinyExpressionP4AST ast) {
    TinyExpressionP4AST root = resultRoot(ast);
    if (root instanceof TinyExpressionP4AST.NumberMatchExpr) return ResultFamily.NUMBER;
    if (root instanceof TinyExpressionP4AST.StringMatchExpr) return ResultFamily.STRING;
    if (root instanceof TinyExpressionP4AST.BooleanMatchExpr) return ResultFamily.BOOLEAN;
    return null;
  }

  private static ResultFamily explicitResultFamily(TinyExpressionP4AST ast) {
    ast = resultRoot(ast);
    ArrayList<ResultFamily> families = new ArrayList<>();
    if (ast instanceof TinyExpressionP4AST.NumberMatchExpr match) {
      addDirectFamily(families, match.firstCase().value().value());
      for (TinyExpressionP4AST.NumberCaseExpr entry : match.moreCases()) {
        addDirectFamily(families, entry.value().value());
      }
      addDirectFamily(families, match.defaultCase().value().value());
    } else if (ast instanceof TinyExpressionP4AST.StringMatchExpr match) {
      addDirectFamily(families, match.firstCase().value().value());
      for (TinyExpressionP4AST.StringCaseExpr entry : match.moreCases()) {
        addDirectFamily(families, entry.value().value());
      }
      addDirectFamily(families, match.defaultCase().value().value());
    } else if (ast instanceof TinyExpressionP4AST.BooleanMatchExpr match) {
      addDirectFamily(families, match.firstCase().value().value());
      for (TinyExpressionP4AST.BooleanCaseExpr entry : match.moreCases()) {
        addDirectFamily(families, entry.value().value());
      }
      addDirectFamily(families, match.defaultCase().value().value());
    } else {
      addDirectFamily(families, ast);
    }
    ResultFamily selected = null;
    for (ResultFamily family : families) {
      if (selected != null && selected != family) {
        throw matchTypeMismatch("captured explicit result hints");
      }
      selected = family;
    }
    return selected;
  }

  private static void addDirectFamily(List<ResultFamily> families, TinyExpressionP4AST ast) {
    ResultFamily family = directFamily(ast);
    if (family != null) families.add(family);
  }

  private static ResultFamily directFamily(TinyExpressionP4AST ast) {
    if (ast instanceof TinyExpressionP4AST.VariableRefExpr variable
        && variable.type().isPresent()) {
      String type = variable.type().orElseThrow().toLowerCase(java.util.Locale.ROOT);
      return switch (type) {
        case "number", "float" -> ResultFamily.NUMBER;
        case "string" -> ResultFamily.STRING;
        case "boolean" -> ResultFamily.BOOLEAN;
        case "object" -> ResultFamily.OBJECT;
        default -> throw new IllegalArgumentException("Unknown explicit result type: " + type);
      };
    }
    if (ast instanceof TinyExpressionP4AST.StringTypedVariableRefExpr
        || ast instanceof TinyExpressionP4AST.StringCastVariableRefExpr) {
      return ResultFamily.STRING;
    }
    if (ast instanceof TinyExpressionP4AST.BinaryExpr binary && binary.op().isEmpty()) {
      return directFamily(binary.left());
    }
    if (ast instanceof TinyExpressionP4AST.StringConcatExpr concat
        && concat.op().isEmpty() && concat.left() instanceof TinyExpressionP4AST value) {
      return directFamily(value);
    }
    if (ast instanceof TinyExpressionP4AST.BooleanOrExpr disjunction
        && disjunction.op().isEmpty()) {
      return directFamily(disjunction.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanAndExpr conjunction
        && conjunction.op().isEmpty()) {
      return directFamily(conjunction.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanXorExpr exclusive
        && exclusive.op().isEmpty()) {
      return directFamily(exclusive.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanFactorExpr factor
        && factor.value() instanceof TinyExpressionP4AST value) {
      return directFamily(value);
    }
    if (ast instanceof TinyExpressionP4AST.ObjectExpr object
        && object.value() instanceof TinyExpressionP4AST value) {
      return directFamily(value);
    }
    return null;
  }

  private static TinyExpressionP4AST resultRoot(TinyExpressionP4AST ast) {
    if (ast instanceof TinyExpressionP4AST.FormulaExpr formula) {
      return resultRoot(formula.expression());
    }
    if (ast instanceof TinyExpressionP4AST.ExpressionExpr expression
        && expression.value() instanceof TinyExpressionP4AST value) {
      return resultRoot(value);
    }
    if (ast instanceof TinyExpressionP4AST.BinaryExpr binary
        && binary.op().isEmpty() && binary.right().isEmpty()) {
      return resultRoot(binary.left());
    }
    if (ast instanceof TinyExpressionP4AST.StringConcatExpr concat
        && concat.op().isEmpty() && concat.right().isEmpty()
        && concat.left() instanceof TinyExpressionP4AST value) {
      return resultRoot(value);
    }
    if (ast instanceof TinyExpressionP4AST.BooleanOrExpr disjunction
        && disjunction.op().isEmpty() && disjunction.right().isEmpty()) {
      return resultRoot(disjunction.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanAndExpr conjunction
        && conjunction.op().isEmpty() && conjunction.right().isEmpty()) {
      return resultRoot(conjunction.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanXorExpr exclusive
        && exclusive.op().isEmpty() && exclusive.right().isEmpty()) {
      return resultRoot(exclusive.left());
    }
    if (ast instanceof TinyExpressionP4AST.BooleanFactorExpr factor
        && factor.value() instanceof TinyExpressionP4AST value) {
      return resultRoot(value);
    }
    return ast;
  }

  private static boolean isDocument(String source, ParsedAst parsed) {
    if (!(parsed.ast() instanceof TinyExpressionP4AST.FormulaExpr formula)) return false;
    if (!formula.imports().isEmpty()
        || !formula.declarations().isEmpty()
        || !formula.methods().isEmpty()) return true;
    try {
      String parserSource = TinyExpressionParserCapabilities
          .stripJavaStyleCommentsPreservingLayout(source);
      return !parserSource.strip().equals(parsed.sourceText().text(formula.expression()).strip());
    } catch (RuntimeException unavailableSpan) {
      // A Formula without an owned expression span is conservatively kept intact.
      return true;
    }
  }

  private static IllegalArgumentException matchTypeMismatch(String source) {
    return new IllegalArgumentException(
        "P4 match type mismatch: case and default values must use one result family: " + source);
  }

  private enum ResultFamily {
    NUMBER,
    STRING,
    BOOLEAN,
    OBJECT
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
   * standard {@code Formula} parse fails or under-consumes, retry the boolean, string, and
   * object family roots in public dispatch order. Explicit type hints on an otherwise successful
   * parse are handled separately from captured AST metadata. These retries add cost only for
   * inputs the standard parse already rejected, leaving the hot path untouched.
   */
  private static ParsedRoot parseRootToken(String source, long deadlineNanos) {
    ParseResult primary = parseWithRoot(TinyExpressionP4Parsers.getRootParser(), source, deadlineNanos);
    if (primary.fullyConsumed(source)) {
      return new ParsedRoot(
          primary.rootToken(), primary.legacyToken(), P4SourceMapping.EntryPoint.ROOT);
    }
    // Retry families in public dispatch order after the primary Formula rejects EOF.
    for (Class<? extends Parser> alternate : List.of(
        TinyExpressionP4Parsers.BooleanExpressionParser.class,
        TinyExpressionP4Parsers.StringExpressionParser.class,
        TinyExpressionP4Parsers.ObjectExpressionParser.class)) {
      ParseResult result = parseWithRoot(Parser.get(alternate), source, deadlineNanos);
      if (result.fullyConsumed(source)) {
        return new ParsedRoot(
            result.rootToken(), result.legacyToken(), P4SourceMapping.EntryPoint.ALTERNATE);
      }
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

  private static boolean isDeferredDiagnosticsSafe(Parser root) {
    Set<Parser> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    var pending = new ArrayDeque<Parser>();
    pending.push(root);
    while (!pending.isEmpty()) {
      Parser parser = pending.pop();
      if (!visited.add(parser)) continue;
      Class<?> type = parser.getClass();
      // The nest includes generated anonymous parsers inside generated rule classes too.
      if (!type.getName().startsWith("org.unlaxer.parser.")
          && type.getNestHost() != TinyExpressionP4Parsers.class
          && !DEFERRED_SAFE_CUSTOM_PARSERS.contains(type)) {
        return false;
      }
      // The shadow AbstractParser/Parser exposes children even for repeats that do not implement
      // HasChildrenParser. Those edges must also be checked, including shared nodes and cycles.
      pending.addAll(parser instanceof HasChildrenParser parent
          ? parent.getChildren() : parser.getChildren());
    }
    return true;
  }

  private static ParseResult parseWithRoot(Parser rootParser, String source, long deadlineNanos) {
    if (DIAGNOSTICS_COMPAT == null) {
      return parseWithRoot(rootParser, source, deadlineNanos, null);
    }
    Object options = DIAGNOSTICS_COMPAT.options(memoizeEnabled(),
        DEFERRED_DIAGNOSTICS_SAFE.computeIfAbsent(
            rootParser, ClassicP4PreferredAstMapper::isDeferredDiagnosticsSafe));
    long retryBudgetNanos = deadlineNanos > 0L ? deadlineNanos - System.nanoTime() : 0L;
    ParseResult result = parseWithRoot(rootParser, source, deadlineNanos, options);
    if (!result.fullyConsumed(source) && DIAGNOSTICS_COMPAT.isDeferred(options)) {
      // The first context is already closed. Retry the same root/input with fresh scope and memo
      // state, using the same time budget. Failure can therefore cost two parse budgets.
      long retryDeadlineNanos = deadlineNanos > 0L ? System.nanoTime() + retryBudgetNanos : 0L;
      return parseWithRoot(rootParser, source, retryDeadlineNanos,
          DIAGNOSTICS_COMPAT.detailed(options));
    }
    return result;
  }

  private static ParseResult parseWithRoot(
      Parser rootParser, String source, long deadlineNanos, Object options) {
    ParseContext context = options == null
        ? new ParseContext(createRootSourceCompat(source))
        : DIAGNOSTICS_COMPAT.context(source, options);
    Parsed parsed;
    int consumed = -1;
    Token rootToken = null;
    Token legacyToken = null;
    try {
      ScopeStore.registerDispatcher(context);
      if (options == null && memoizeEnabled()) {
        try {
          context.enableMemoize();
        } catch (NoSuchMethodError unavailableBeforeMemoization) {
          // Keep the original published-version path: memoization is an optional optimization.
        }
      }
      if (deadlineNanos > 0L) {
        registerDeadlineListener(context, deadlineNanos);
      }
      parsed = rootParser.parse(context);
      if (parsed.isSucceeded()) {
        consumed = consumedLengthCompat(parsed.getConsumed());
        rootToken = parsed.getRootToken(false);
        legacyToken = parsed.getRootToken(true);
        for (Token committed : context.getCurrent().getTokens()) {
          if (committed.parser == rootParser) {
            rootToken = committed;
            break;
          }
        }
      }
    } finally {
      closeParseContextQuietly(context);
    }
    if (!parsed.isSucceeded()) {
      return new ParseResult(false, -1, null, null);
    }
    return new ParseResult(true, consumed, rootToken, legacyToken);
  }

  /** Reflection boundary: none of the development-only types occur in bytecode signatures. */
  private record DiagnosticsCompat(
      Method withMemoization, Method resolveDiagnostics, Method withDiagnostics,
      Method diagnostics, Method withOptions,
      Object safeFailures, Object off, Object detailed, Object deferred) {
    static DiagnosticsCompat resolve() {
      try {
        Class<?> options = Class.forName("org.unlaxer.context.ParseOptions");
        Class<?> memoization = Class.forName("org.unlaxer.context.Memoization");
        Class<?> policy = Class.forName("org.unlaxer.context.ParseOptions$Diagnostics");
        return new DiagnosticsCompat(
            options.getMethod("withMemoization", memoization),
            options.getMethod("resolveDiagnostics", boolean.class),
            options.getMethod("withDiagnostics", policy),
            options.getMethod("diagnostics"),
            ParseContext.class.getMethod("withOptions", Source.class, options, ParseContextEffector[].class),
            memoization.getField("SAFE_FAILURES").get(null), memoization.getField("OFF").get(null),
            policy.getField("DETAILED").get(null), policy.getField("DETAILED_ON_FAILURE").get(null));
      } catch (ReflectiveOperationException | LinkageError unavailableInPublishedVersion) {
        return null;
      }
    }

    Object options(boolean memoize, boolean safe) {
      Object options = invoke(withMemoization, null, memoize ? safeFailures : off);
      return invoke(resolveDiagnostics, options, safe);
    }

    boolean isDeferred(Object options) {
      return invoke(diagnostics, options) == deferred;
    }

    Object detailed(Object options) {
      return invoke(withDiagnostics, options, detailed);
    }

    ParseContext context(String source, Object options) {
      return (ParseContext) invoke(withOptions, null,
          createRootSourceCompat(source), options, new ParseContextEffector[0]);
    }

    private static Object invoke(Method method, Object receiver, Object... args) {
      try {
        return method.invoke(receiver, args);
      } catch (InvocationTargetException failure) {
        // Once available, API failures must propagate rather than silently switching semantics.
        if (failure.getCause() instanceof RuntimeException cause) throw cause;
        if (failure.getCause() instanceof Error cause) throw cause;
        throw new IllegalStateException("cannot invoke P4 diagnostics API", failure.getCause());
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("cannot invoke P4 diagnostics API", failure);
      }
    }
  }

  private record ParsedRoot(
      Token token, Token legacyToken, P4SourceMapping.EntryPoint entryPoint) {}

  private record ParseResult(boolean succeeded, int consumed, Token rootToken, Token legacyToken) {
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
    org.unlaxer.Name name = org.unlaxer.Name.of(ClassicP4PreferredAstMapper.class, "parseDeadline");
    org.unlaxer.listener.TransactionListener listener =
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
        };
    try {
      ParseContext.class
          .getMethod("addMemoizationTransparentTransactionListener",
              org.unlaxer.Name.class, org.unlaxer.listener.TransactionListener.class)
          .invoke(context, name, listener);
    } catch (NoSuchMethodException unavailableBeforeSafeMemoization) {
      context.addTransactionListener(name, listener);
    } catch (ReflectiveOperationException registrationFailure) {
      throw new IllegalStateException("cannot register P4 parse deadline listener", registrationFailure);
    }
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
