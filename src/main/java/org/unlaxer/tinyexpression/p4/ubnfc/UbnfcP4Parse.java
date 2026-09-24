package org.unlaxer.tinyexpression.p4.ubnfc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseResult;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/**
 * ubnfc 生成パーサを使う P4 ファサードの中身。
 *
 * <p>公開面は {@link org.unlaxer.tinyexpression.p4.P4PreferredAstMapper} が持ち、
 * {@link org.unlaxer.tinyexpression.p4.P4ParserEngine#UBNFC}（2.0.0 の既定）のときここへ委譲する。
 * {@code ParsedAst} は facade の nested record なので、ここでは中立の {@link Result} を返し、
 * facade が包み直す。移植元は ubnfc {@code examples/p4-java-facade}（UBNFC_PIN の commit）。
 *
 * <p>旧実装との対応:
 * <ul>
 *   <li>候補列（{@code preferredAstSimpleNames} 系）と {@code selectExplicitResultFamily} の
 *       分岐は旧実装から移した。順序・fallback・{@code selectionMode} の文字列は同一。</li>
 *   <li>旧実装の「トークン木から preferred な AST を選ぶ」探索は、typed AST 上の同じ順序
 *       （preferred 優先 → 最小深さ → 開始位置が大きい方）に置き換えた。</li>
 *   <li>期限は解析の前後でのみ見る。ubnfc 生成パーサは packrat + 深さ上限で、旧実装のような
 *       指数バックトラック（issue #19/#20）を起こさないため、解析途中で割り込む必要がない。</li>
 * </ul>
 */
public final class UbnfcP4Parse {

    private UbnfcP4Parse() {}

    /** {@code ParsedAst} と同じ 3 つ組。呼び出し側の nested record へ詰め替えるための中立形。 */
    public record Result(TinyExpressionP4AST ast, String selectionMode, P4SourceText sourceText) {}

    /** パース期限超過。旧ファサードと同じく明示的なパース失敗として扱う。 */
    public static final class ParseDeadlineExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ParseDeadlineExceededException(String message) {
            super(message);
        }
    }

    // ---------------------------------------------------------------- 入口

    public static long defaultParseDeadlineNanos() {
        long timeoutMillis = Long.getLong("tinyexpression.p4.parse.timeout.millis", 10_000L);
        if (timeoutMillis <= 0L) {
            return 0L;
        }
        return System.nanoTime() + timeoutMillis * 1_000_000L;
    }

    public static Result parseDetailed(String formula, ExpressionType preferredResultType) {
        String source = formula == null ? "" : formula;
        List<String> candidates = preferredAstSimpleNames(source, preferredResultType);
        long deadlineNanos = defaultParseDeadlineNanos();
        Result parsed = parseMappedCandidates(
            source, candidates, preferredResultType == null, deadlineNanos);
        return selectExplicitResultFamily(source, candidates, parsed, deadlineNanos);
    }

    public static Result parseByAstSimpleNameDetailed(
            String formula, String preferredAstSimpleName, long deadlineNanos) {
        String source = formula == null ? "" : formula;
        return parseMappedCandidates(
            source,
            preferredAstSimpleName == null ? List.of() : List.of(preferredAstSimpleName),
            preferredAstSimpleName == null,
            deadlineNanos);
    }

    public static Result parseByAstSimpleNamesDetailed(
            String formula, List<String> candidates, long deadlineNanos) {
        String source = formula == null ? "" : formula;
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("No generated AST candidates supplied");
        }
        Result parsed = parseMappedCandidates(source, candidates, false, deadlineNanos);
        int parsedIndex = candidates.indexOf(parsed.ast().getClass().getSimpleName());
        boolean earlierTypedFamily = parsedIndex > 0 && candidates.subList(0, parsedIndex).stream()
            .anyMatch(UbnfcP4Parse::isTypedFamilyRoot);
        if (!earlierTypedFamily) {
            return parsed;
        }
        Result selected = selectExplicitResultFamily(source, candidates, parsed, deadlineNanos);
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

    // ------------------------------------------------------------- 候補の列

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

    private enum CandidateProfile { PREFERRED, AST_EVALUATOR, GENERATED_VALUE, DECLARATION }

    // ------------------------------------------------------------ 解析と選択

    /**
     * 1 回の解析の結果。{@code root} が入口ルールの typed AST、{@code spans} はその全ノード、
     * {@code bestByName} は「クラス単純名 -> その名前の最良の節点」を 1 回の走査で作った索引。
     * 候補は 40 件を超えることがあるので、候補ごとに木を歩き直すと入力長 × 候補数になる。
     */
    private record ParsedRoot(TinyExpressionP4AST root, P4SourceText sourceText,
                              Map<Object, int[]> spans,
                              Map<String, TinyExpressionP4AST> bestByName) {}

    private static Result parseMappedCandidates(
            String source, List<String> candidates, boolean allowDefault, long deadlineNanos) {
        // 生成文法の interleave metadata が入れ子の alternative に及ばないので、
        // オフセットを保ったままコメントだけ空白に置き換える（旧実装と同じ前処理）。
        String parserSource =
            TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
        ParsedRoot parsedRoot = parseRootToken(parserSource, deadlineNanos);
        return mapCandidates(parserSource, candidates, allowDefault, parsedRoot);
    }

    /**
     * 旧実装と同じ入口順で解析する。生成文法の {@code Expression} は NumberExpression を先に
     * 試すため、素の真偽比較（{@code 1 > 0 & 2 > 1}）は Formula が EOF で失敗する (issue #23)。
     * 失敗・部分消費のときだけ boolean / string / object の族ルートを公開ディスパッチ順に再試行する。
     */
    private static ParsedRoot parseRootToken(String parserSource, long deadlineNanos) {
        checkDeadline(deadlineNanos);
        ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> primary =
            parseEntry("Formula", parserSource);
        if (fullyConsumed(primary, parserSource)) {
            return toParsedRoot(primary, parserSource);
        }
        for (String alternate : List.of("BooleanExpression", "StringExpression", "ObjectExpression")) {
            checkDeadline(deadlineNanos);
            ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> result =
                parseEntry(alternate, parserSource);
            if (fullyConsumed(result, parserSource)) {
                return toParsedRoot(result, parserSource);
            }
        }
        checkDeadline(deadlineNanos);
        if (!primary.ok()) {
            throw new IllegalArgumentException("Parse failed: " + parserSource);
        }
        throw new IllegalArgumentException(
            "Parse failed at offset " + utf16Offset(parserSource, primary.consumedCp())
                + ": " + parserSource);
    }

    /**
     * 全消費を要求しない形で解析する。旧実装は「成功したが消費が足りない」と「失敗」を
     * 区別してメッセージを変えるので、こちらもその区別が要る。
     */
    private static ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> parseEntry(
            String entry, String parserSource) {
        ParseOptions options = new ParseOptions(true, false, false, true, P4Scanners.ALL);
        return TinyExpressionP4Parser.parseEntry("TinyExpressionP4", entry, parserSource, options);
    }

    private static boolean fullyConsumed(
            ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> result, String source) {
        return result.ok() && result.ast().isPresent()
            && result.consumedCp() == source.codePointCount(0, source.length());
    }

    private static ParsedRoot toParsedRoot(
            ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> result, String parserSource) {
        UbnfcAstConverter converter = new UbnfcAstConverter(result.nodeSpans());
        TinyExpressionP4AST root = converter.convert(result.ast().orElseThrow());
        Map<Object, int[]> spans = converter.spans();
        return new ParsedRoot(root, snapshot(parserSource, spans), spans, converter.bestByName());
    }

    private static P4SourceText snapshot(String parserSource, Map<Object, int[]> spans) {
        return P4SourceText.fromSnapshot(parserSource, node -> {
            int[] span = spans.get(node);
            return span == null ? Optional.empty() : Optional.of(span.clone());
        });
    }

    private static Result mapCandidates(String parserSource, List<String> candidates,
            boolean allowDefault, ParsedRoot parsedRoot) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            TinyExpressionP4AST selected = select(parsedRoot, candidate);
            if (selected == null || !coversWholeSource(parserSource, parsedRoot, selected)) {
                continue;
            }
            if (candidate.equals(selected.getClass().getSimpleName())) {
                return new Result(selected, "preferred:" + candidate, parsedRoot.sourceText());
            }
        }
        if (allowDefault) {
            return new Result(parsedRoot.root(), "default", parsedRoot.sourceText());
        }
        throw new IllegalArgumentException(
            "No whole-source generated AST mapping found: " + parserSource);
    }

    /**
     * 索引は {@link UbnfcAstConverter} が変換の走査に相乗りして作る（反射なし、木を 1 回だけ歩く）。
     * 基準は旧生成 mapper の {@code findBestMappedToken}
     * （unlaxer-dsl の {@code MapperRuleEmitter#emitFindBestMappedToken}）と同じで、
     * 「preferred 一致を最優先 -> 最小深さ -> 開始位置が大きい方 -> 後に見た方」。
     *
     * <p>旧側はトークン木を歩き、こちらは typed AST を歩く。深さの数え方も木が違うぶん一致しない。
     * ただし選んだ節点は直後に {@link #coversWholeSource} で全ソースを覆うものだけに絞られるため、
     * 結果は変わらない。これは主張ではなく、パリティ 340 件で確かめた事実
     * （docs/reports/2026-09-24-te-facade.md）。
     */
    /** preferred が無い、または該当が無いときの答えは深さ 0、すなわちルート（旧側も同じ）。 */
    private static TinyExpressionP4AST select(ParsedRoot parsedRoot, String preferred) {
        if (preferred == null || preferred.isBlank()) {
            return parsedRoot.root();
        }
        return parsedRoot.bestByName().getOrDefault(preferred, parsedRoot.root());
    }

    private static boolean coversWholeSource(
            String source, ParsedRoot parsedRoot, TinyExpressionP4AST node) {
        int[] span = parsedRoot.spans().get(node);
        if (span == null) {
            return false;
        }
        return source.strip().equals(textOf(source, span).strip());
    }

    private static String textOf(String source, int[] span) {
        return source.substring(source.offsetByCodePoints(0, span[0]),
            source.offsetByCodePoints(0, span[1]));
    }

    private static void checkDeadline(long deadlineNanos) {
        if (deadlineNanos > 0L && System.nanoTime() > deadlineNanos) {
            throw new ParseDeadlineExceededException("P4 parse exceeded deadline");
        }
    }

    private static int utf16Offset(String source, int codePointOffset) {
        int total = source.codePointCount(0, source.length());
        return source.offsetByCodePoints(0, Math.max(0, Math.min(codePointOffset, total)));
    }

    // -------------------------------------------------- 明示された結果型の再選択

    private enum ResultFamily { NUMBER, STRING, BOOLEAN, OBJECT }

    private static Result selectExplicitResultFamily(
            String source, List<String> candidates, Result parsed, long deadlineNanos) {
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
        String documentSource =
            TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
        String parserSource = documentSource;
        TinyExpressionP4AST.FormulaExpr documentRoot =
            document && parsed.ast() instanceof TinyExpressionP4AST.FormulaExpr formula
                ? formula : null;
        int documentExpressionOffset = 0;
        if (documentRoot != null) {
            int[] span = parsed.sourceText().spanOf(documentRoot.expression())
                .orElseThrow(() -> new IllegalArgumentException(
                    "No owned source span for Formula expression"));
            documentExpressionOffset = span[0];
            parserSource = parserSource.substring(
                parserSource.offsetByCodePoints(0, span[0]),
                parserSource.offsetByCodePoints(0, span[1]));
        }
        String entry = switch (family) {
            case STRING -> currentMatchFamily != null ? "StringMatchExpression" : "StringExpression";
            case BOOLEAN -> currentMatchFamily != null ? "BooleanMatchExpression" : "BooleanExpression";
            case OBJECT -> "ObjectExpression";
            case NUMBER -> {
                if (currentMatchFamily != null) {
                    yield "NumberMatchExpression";
                }
                throw new IllegalStateException("number family already selected");
            }
        };
        checkDeadline(deadlineNanos);
        ParseResult<org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST> reparsed =
            parseEntry(entry, parserSource);
        if (!fullyConsumed(reparsed, parserSource)) {
            if (currentMatchFamily != null) throw matchTypeMismatch(source);
            throw new IllegalArgumentException("Explicit result type could not be parsed as "
                + family.name().toLowerCase(Locale.ROOT) + ": " + source);
        }
        ArrayList<String> familyCandidates = new ArrayList<>();
        addIfAbsent(familyCandidates, switch (family) {
            case STRING -> currentMatchFamily != null ? "StringMatchExpr" : "StringConcatExpr";
            case BOOLEAN -> currentMatchFamily != null ? "BooleanMatchExpr" : "BooleanOrExpr";
            case OBJECT -> "ObjectExpr";
            case NUMBER -> null;
        });
        for (String candidate : candidates) addIfAbsent(familyCandidates, candidate);
        ParsedRoot reparsedRoot = toParsedRoot(reparsed, parserSource);
        Result selected = mapCandidates(parserSource, familyCandidates, true, reparsedRoot);
        if (family == ResultFamily.OBJECT) {
            if (selected.ast() instanceof TinyExpressionP4AST.ObjectExpr) {
                selected = new Result(selected.ast(), "explicit:object", selected.sourceText());
            } else {
                TinyExpressionP4AST.ObjectExpr object =
                    new TinyExpressionP4AST.ObjectExpr(selected.ast());
                selected = new Result(object, "explicit:object",
                    selected.sourceText().withWholeSourceNode(object));
            }
        }
        if (documentRoot == null) return selected;

        TinyExpressionP4AST.ExpressionExpr expression =
            new TinyExpressionP4AST.ExpressionExpr(selected.ast());
        TinyExpressionP4AST.FormulaExpr formula = new TinyExpressionP4AST.FormulaExpr(
            documentRoot.imports(), documentRoot.declarations(), expression, documentRoot.methods());
        P4SourceText sourceText = withOverlay(documentSource, parsed.sourceText(),
            selected.sourceText(), documentExpressionOffset,
            formula, documentRoot, expression, documentRoot.expression());
        return new Result(formula, "document:" + selected.selectionMode(), sourceText);
    }

    /**
     * 再解析した部分木の snapshot を文書側の snapshot に重ねる。旧実装の
     * {@code P4SourceText#withOverlay}（package private）と同じ規則を public API だけで組む。
     * 座標系は両者とも同じ code point 空間で、{@code offset} は文書内の式の開始位置。
     */
    private static P4SourceText withOverlay(String parserSource, P4SourceText base,
            P4SourceText overlay, int offset, Object newOuter, Object oldOuter,
            Object newExpression, Object oldExpression) {
        return P4SourceText.fromSnapshot(parserSource, value -> {
            if (value == newOuter) return base.spanOf(oldOuter);
            if (value == newExpression) return base.spanOf(oldExpression);
            Optional<int[]> overlaid = overlay.spanOf(value);
            if (overlaid.isPresent()) {
                int[] range = overlaid.orElseThrow().clone();
                range[0] += offset;
                range[1] += offset;
                return Optional.of(range);
            }
            return base.spanOf(value);
        });
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
            String type = variable.type().orElseThrow().toLowerCase(Locale.ROOT);
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

    private static boolean isDocument(String source, Result parsed) {
        if (!(parsed.ast() instanceof TinyExpressionP4AST.FormulaExpr formula)) return false;
        if (!formula.imports().isEmpty()
            || !formula.declarations().isEmpty()
            || !formula.methods().isEmpty()) return true;
        try {
            String parserSource = TinyExpressionParserCapabilities
                .stripJavaStyleCommentsPreservingLayout(source);
            return !parserSource.strip().equals(parsed.sourceText().text(formula.expression()).strip());
        } catch (RuntimeException unavailableSpan) {
            return true;
        }
    }

    private static IllegalArgumentException matchTypeMismatch(String source) {
        return new IllegalArgumentException(
            "P4 match type mismatch: case and default values must use one result family: " + source);
    }
}
