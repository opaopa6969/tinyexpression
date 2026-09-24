package org.unlaxer.tinyexpression.p4.ubnfc;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Span;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;

/**
 * ubnfc 生成 typed AST -> tinyexpression 生成 AST の 1:1 変換。
 *
 * <p>{@code scripts/generate-ubnfc-converter.py} が両側の record 定義から機械生成する。
 * 手で編集しない。86 record すべてで名前・component 名・順序が一致しており、
 * 変換は構造を変えない（span も code point 半開区間のまま引き継ぐ）。
 */
final class UbnfcAstConverter {
    private final Map<Object, Span> sourceSpans;
    private final String source;
    private final Map<Object, int[]> spans = new IdentityHashMap<>();
    private final Map<String, TinyExpressionP4AST> bestByName = new HashMap<>();
    private final Map<String, int[]> bestRank = new HashMap<>();
    private int depth;

    UbnfcAstConverter(Map<Object, Span> sourceSpans, String source) {
        this.sourceSpans = sourceSpans;
        this.source = source;
    }

    /** 節点の字面（code point 区間、strip 済み）。公開 3.0.15 の {@code firstTokenText} + {@code stripQuotes} と同じ。 */
    private String sourceText(org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST node) {
        Span span = sourceSpans.get(node);
        if (span == null) {
            throw new IllegalStateException("no source span for " + node.getClass().getSimpleName());
        }
        String text = source.substring(source.offsetByCodePoints(0, span.start()),
            source.offsetByCodePoints(0, span.end())).strip();
        return text.length() >= 2 && text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\''
            ? text.substring(1, text.length() - 1) : text;
    }

    /** 変換後ノード -> code point 半開区間。identity で引く。 */
    Map<Object, int[]> spans() {
        return spans;
    }

    /**
     * クラス単純名 -> その名前の最良の節点。基準は旧生成 mapper の
     * {@code findBestMappedToken} と同じ「最小深さ -> 開始位置が大きい方 -> 後に見た方」。
     * 変換の走査に相乗りするので、候補ごとに木を歩き直さない。
     */
    Map<String, TinyExpressionP4AST> bestByName() {
        return bestByName;
    }

    java.lang.Object convertAny(java.lang.Object value) {
        if (value instanceof org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST node) {
            return convert(node);
        }
        return value;
    }

    TinyExpressionP4AST convert(org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST node) {
        if (node == null) {
            return null;
        }
        int nodeDepth = depth++;
        TinyExpressionP4AST converted = switch (node) {
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.FormulaExpr n -> new TinyExpressionP4AST.FormulaExpr(n.imports().stream().map(v -> (TinyExpressionP4AST.ImportDeclarationExpr) convert(v)).toList(), n.declarations().stream().map(this::convertAny).toList(), (TinyExpressionP4AST.ExpressionExpr) convert(n.expression()), n.methods().stream().map(this::convertAny).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.CodeBlockExpr n -> new TinyExpressionP4AST.CodeBlockExpr();
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ImportDeclarationExpr n -> new TinyExpressionP4AST.ImportDeclarationExpr((TinyExpressionP4AST.QualifiedNameExpr) convert(n.className()), n.method(), n.alias());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.QualifiedNameExpr n -> new TinyExpressionP4AST.QualifiedNameExpr(n.head(), n.tail());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberVariableDeclarationExpr n -> new TinyExpressionP4AST.NumberVariableDeclarationExpr(n.varName(), n.onlyIfAbsent().<TinyExpressionP4AST.OnlyIfAbsentExpr>map(v -> (TinyExpressionP4AST.OnlyIfAbsentExpr) convert(v)), n.value().<TinyExpressionP4AST.BinaryExpr>map(v -> (TinyExpressionP4AST.BinaryExpr) convert(v)), n.desc());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringVariableDeclarationExpr n -> new TinyExpressionP4AST.StringVariableDeclarationExpr(n.varName(), n.onlyIfAbsent().<TinyExpressionP4AST.OnlyIfAbsentExpr>map(v -> (TinyExpressionP4AST.OnlyIfAbsentExpr) convert(v)), n.value().<TinyExpressionP4AST.StringConcatExpr>map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)), n.desc());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanVariableDeclarationExpr n -> new TinyExpressionP4AST.BooleanVariableDeclarationExpr(n.varName(), n.onlyIfAbsent().<TinyExpressionP4AST.OnlyIfAbsentExpr>map(v -> (TinyExpressionP4AST.OnlyIfAbsentExpr) convert(v)), n.value().<TinyExpressionP4AST.BooleanOrExpr>map(v -> (TinyExpressionP4AST.BooleanOrExpr) convert(v)), n.desc());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ObjectVariableDeclarationExpr n -> new TinyExpressionP4AST.ObjectVariableDeclarationExpr(n.varName(), n.onlyIfAbsent().<TinyExpressionP4AST.OnlyIfAbsentExpr>map(v -> (TinyExpressionP4AST.OnlyIfAbsentExpr) convert(v)), n.value().<TinyExpressionP4AST.ObjectExpr>map(v -> (TinyExpressionP4AST.ObjectExpr) convert(v)), n.desc());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.OnlyIfAbsentExpr n -> new TinyExpressionP4AST.OnlyIfAbsentExpr();
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberMethodDeclarationExpr n -> new TinyExpressionP4AST.NumberMethodDeclarationExpr(n.methodName(), n.parameters().<TinyExpressionP4AST.MethodParametersExpr>map(v -> (TinyExpressionP4AST.MethodParametersExpr) convert(v)), (TinyExpressionP4AST.BinaryExpr) convert(n.expression()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringMethodDeclarationExpr n -> new TinyExpressionP4AST.StringMethodDeclarationExpr(n.methodName(), n.parameters().<TinyExpressionP4AST.MethodParametersExpr>map(v -> (TinyExpressionP4AST.MethodParametersExpr) convert(v)), (TinyExpressionP4AST.StringConcatExpr) convert(n.expression()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanMethodDeclarationExpr n -> new TinyExpressionP4AST.BooleanMethodDeclarationExpr(n.methodName(), n.parameters().<TinyExpressionP4AST.MethodParametersExpr>map(v -> (TinyExpressionP4AST.MethodParametersExpr) convert(v)), (TinyExpressionP4AST.BooleanOrExpr) convert(n.expression()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ObjectMethodDeclarationExpr n -> new TinyExpressionP4AST.ObjectMethodDeclarationExpr(n.methodName(), n.parameters().<TinyExpressionP4AST.MethodParametersExpr>map(v -> (TinyExpressionP4AST.MethodParametersExpr) convert(v)), (TinyExpressionP4AST.ObjectExpr) convert(n.expression()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.MethodParametersExpr n -> new TinyExpressionP4AST.MethodParametersExpr(n.values().stream().map(v -> (TinyExpressionP4AST.MethodParameterExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.MethodParameterExpr n -> new TinyExpressionP4AST.MethodParameterExpr(n.paramName(), n.type());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExternalBooleanInvocationExpr n -> (TinyExpressionP4AST.ExternalBooleanInvocationExpr) VariantShapes.construct(TinyExpressionP4AST.ExternalBooleanInvocationExpr.class, new VariantShapes.OptionalNode(n.className().map(v -> (java.lang.Object) convert(v))), n.name(), n.args().<TinyExpressionP4AST.ArgumentsExpr>map(v -> (TinyExpressionP4AST.ArgumentsExpr) convert(v)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExternalNumberInvocationExpr n -> (TinyExpressionP4AST.ExternalNumberInvocationExpr) VariantShapes.construct(TinyExpressionP4AST.ExternalNumberInvocationExpr.class, new VariantShapes.OptionalNode(n.className().map(v -> (java.lang.Object) convert(v))), n.name(), n.args().<TinyExpressionP4AST.ArgumentsExpr>map(v -> (TinyExpressionP4AST.ArgumentsExpr) convert(v)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExternalStringInvocationExpr n -> (TinyExpressionP4AST.ExternalStringInvocationExpr) VariantShapes.construct(TinyExpressionP4AST.ExternalStringInvocationExpr.class, new VariantShapes.OptionalNode(n.className().map(v -> (java.lang.Object) convert(v))), n.name(), n.args().<TinyExpressionP4AST.ArgumentsExpr>map(v -> (TinyExpressionP4AST.ArgumentsExpr) convert(v)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExternalObjectInvocationExpr n -> (TinyExpressionP4AST.ExternalObjectInvocationExpr) VariantShapes.construct(TinyExpressionP4AST.ExternalObjectInvocationExpr.class, new VariantShapes.OptionalNode(n.className().map(v -> (java.lang.Object) convert(v))), n.name(), n.args().<TinyExpressionP4AST.ArgumentsExpr>map(v -> (TinyExpressionP4AST.ArgumentsExpr) convert(v)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.MethodInvocationExpr n -> new TinyExpressionP4AST.MethodInvocationExpr(n.name(), n.args().<TinyExpressionP4AST.ArgumentsExpr>map(v -> (TinyExpressionP4AST.ArgumentsExpr) convert(v)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.TernaryExpr n -> new TinyExpressionP4AST.TernaryExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.condition()), (TinyExpressionP4AST.BranchExpressionExpr) convert(n.thenExpr()), (TinyExpressionP4AST.BranchExpressionExpr) convert(n.elseExpr()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ArgumentExpressionExpr n -> new TinyExpressionP4AST.ArgumentExpressionExpr(convertAny(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ArgumentsExpr n -> new TinyExpressionP4AST.ArgumentsExpr(n.values().stream().map(v -> (TinyExpressionP4AST.ArgumentExpressionExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BinaryExpr n -> new TinyExpressionP4AST.BinaryExpr(convert(n.left()), n.op(), n.right().stream().map(this::convert).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.SinExpr n -> new TinyExpressionP4AST.SinExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.CosExpr n -> new TinyExpressionP4AST.CosExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.TanExpr n -> new TinyExpressionP4AST.TanExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.SqrtExpr n -> new TinyExpressionP4AST.SqrtExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.MinExpr n -> new TinyExpressionP4AST.MinExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.first()), n.rest().stream().map(v -> (TinyExpressionP4AST.ArgumentExpressionExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.MaxExpr n -> new TinyExpressionP4AST.MaxExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.first()), n.rest().stream().map(v -> (TinyExpressionP4AST.ArgumentExpressionExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.RandomExpr n -> new TinyExpressionP4AST.RandomExpr();
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.AbsExpr n -> new TinyExpressionP4AST.AbsExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.RoundExpr n -> new TinyExpressionP4AST.RoundExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.CeilExpr n -> new TinyExpressionP4AST.CeilExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.FloorExpr n -> new TinyExpressionP4AST.FloorExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.PowExpr n -> new TinyExpressionP4AST.PowExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.base()), (TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.exponent()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.LogExpr n -> new TinyExpressionP4AST.LogExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExpExpr n -> new TinyExpressionP4AST.ExpExpr((TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.arg()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ToNumExpr n -> new TinyExpressionP4AST.ToNumExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()), (TinyExpressionP4AST.ArgumentExpressionExpr) convert(n.defaultValue()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ToUpperCaseExpr n -> new TinyExpressionP4AST.ToUpperCaseExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ToLowerCaseExpr n -> new TinyExpressionP4AST.ToLowerCaseExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.TrimExpr n -> new TinyExpressionP4AST.TrimExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.LengthExpr n -> new TinyExpressionP4AST.LengthExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ToUpperCaseDotExpr n -> new TinyExpressionP4AST.ToUpperCaseDotExpr((TinyExpressionP4AST.VariableRefExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ToLowerCaseDotExpr n -> new TinyExpressionP4AST.ToLowerCaseDotExpr((TinyExpressionP4AST.VariableRefExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.TrimDotExpr n -> new TinyExpressionP4AST.TrimDotExpr((TinyExpressionP4AST.VariableRefExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.LengthDotExpr n -> new TinyExpressionP4AST.LengthDotExpr((TinyExpressionP4AST.VariableRefExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StartsWithExpr n -> new TinyExpressionP4AST.StartsWithExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.EndsWithExpr n -> new TinyExpressionP4AST.EndsWithExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ContainsExpr n -> new TinyExpressionP4AST.ContainsExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.InExpr n -> new TinyExpressionP4AST.InExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()), n.candidates().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StartsWithDotExpr n -> new TinyExpressionP4AST.StartsWithDotExpr(convertAny(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.EndsWithDotExpr n -> new TinyExpressionP4AST.EndsWithDotExpr(convertAny(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ContainsDotExpr n -> new TinyExpressionP4AST.ContainsDotExpr(convertAny(n.value()), n.patterns().stream().map(v -> (TinyExpressionP4AST.StringConcatExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.IsPresentExpr n -> new TinyExpressionP4AST.IsPresentExpr((TinyExpressionP4AST.VariableRefExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.InTimeRangeExpr n -> new TinyExpressionP4AST.InTimeRangeExpr((TinyExpressionP4AST.BinaryExpr) convert(n.startHour()), (TinyExpressionP4AST.BinaryExpr) convert(n.endHour()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.InDayTimeRangeExpr n -> new TinyExpressionP4AST.InDayTimeRangeExpr(n.startDay(), (TinyExpressionP4AST.BinaryExpr) convert(n.startHour()), n.endDay(), (TinyExpressionP4AST.BinaryExpr) convert(n.endHour()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.SliceExpr n -> (TinyExpressionP4AST.SliceExpr) VariantShapes.construct(TinyExpressionP4AST.SliceExpr.class, convertAny(n.value()), new VariantShapes.SliceIndex(n.start().map(v -> (java.lang.Object) convert(v)), n.start().map(this::sourceText)), new VariantShapes.SliceIndex(n.end().map(v -> (java.lang.Object) convert(v)), n.end().map(this::sourceText)), new VariantShapes.SliceIndex(n.step().map(v -> (java.lang.Object) convert(v)), n.step().map(this::sourceText)));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringConcatExpr n -> new TinyExpressionP4AST.StringConcatExpr(convertAny(n.left()), n.op(), n.right().stream().map(this::convertAny).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringCastVariableRefExpr n -> new TinyExpressionP4AST.StringCastVariableRefExpr(n.name());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringTypedVariableRefExpr n -> new TinyExpressionP4AST.StringTypedVariableRefExpr(n.name());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanOrExpr n -> new TinyExpressionP4AST.BooleanOrExpr((TinyExpressionP4AST.BooleanAndExpr) convert(n.left()), n.op(), n.right().stream().map(v -> (TinyExpressionP4AST.BooleanAndExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanAndExpr n -> new TinyExpressionP4AST.BooleanAndExpr((TinyExpressionP4AST.BooleanXorExpr) convert(n.left()), n.op(), n.right().stream().map(v -> (TinyExpressionP4AST.BooleanXorExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanXorExpr n -> new TinyExpressionP4AST.BooleanXorExpr((TinyExpressionP4AST.BooleanFactorExpr) convert(n.left()), n.op(), n.right().stream().map(v -> (TinyExpressionP4AST.BooleanFactorExpr) convert(v)).toList());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NotExpr n -> new TinyExpressionP4AST.NotExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanEqualityExpr n -> new TinyExpressionP4AST.BooleanEqualityExpr(convertAny(n.left()), n.op(), convertAny(n.right()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanFactorExpr n -> new TinyExpressionP4AST.BooleanFactorExpr(convertAny(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringComparisonExpr n -> new TinyExpressionP4AST.StringComparisonExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.left()), n.op(), (TinyExpressionP4AST.StringConcatExpr) convert(n.right()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ComparisonExpr n -> new TinyExpressionP4AST.ComparisonExpr((TinyExpressionP4AST.BinaryExpr) convert(n.left()), n.op(), (TinyExpressionP4AST.BinaryExpr) convert(n.right()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ObjectExpr n -> new TinyExpressionP4AST.ObjectExpr(convertAny(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.IfExpr n -> new TinyExpressionP4AST.IfExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.condition()), (TinyExpressionP4AST.BranchExpressionExpr) convert(n.thenExpr()), (TinyExpressionP4AST.BranchExpressionExpr) convert(n.elseExpr()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BranchExpressionExpr n -> new TinyExpressionP4AST.BranchExpressionExpr(convertAny(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberMatchExpr n -> new TinyExpressionP4AST.NumberMatchExpr((TinyExpressionP4AST.NumberCaseExpr) convert(n.firstCase()), n.moreCases().stream().map(v -> (TinyExpressionP4AST.NumberCaseExpr) convert(v)).toList(), (TinyExpressionP4AST.NumberDefaultCaseExpr) convert(n.defaultCase()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberCaseExpr n -> new TinyExpressionP4AST.NumberCaseExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.condition()), (TinyExpressionP4AST.NumberCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberDefaultCaseExpr n -> new TinyExpressionP4AST.NumberDefaultCaseExpr((TinyExpressionP4AST.NumberCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.NumberCaseValueExpr n -> new TinyExpressionP4AST.NumberCaseValueExpr((TinyExpressionP4AST.BinaryExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringMatchExpr n -> new TinyExpressionP4AST.StringMatchExpr((TinyExpressionP4AST.StringCaseExpr) convert(n.firstCase()), n.moreCases().stream().map(v -> (TinyExpressionP4AST.StringCaseExpr) convert(v)).toList(), (TinyExpressionP4AST.StringDefaultCaseExpr) convert(n.defaultCase()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringCaseExpr n -> new TinyExpressionP4AST.StringCaseExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.condition()), (TinyExpressionP4AST.StringCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringDefaultCaseExpr n -> new TinyExpressionP4AST.StringDefaultCaseExpr((TinyExpressionP4AST.StringCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.StringCaseValueExpr n -> new TinyExpressionP4AST.StringCaseValueExpr((TinyExpressionP4AST.StringConcatExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanMatchExpr n -> new TinyExpressionP4AST.BooleanMatchExpr((TinyExpressionP4AST.BooleanCaseExpr) convert(n.firstCase()), n.moreCases().stream().map(v -> (TinyExpressionP4AST.BooleanCaseExpr) convert(v)).toList(), (TinyExpressionP4AST.BooleanDefaultCaseExpr) convert(n.defaultCase()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanCaseExpr n -> new TinyExpressionP4AST.BooleanCaseExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.condition()), (TinyExpressionP4AST.BooleanCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanDefaultCaseExpr n -> new TinyExpressionP4AST.BooleanDefaultCaseExpr((TinyExpressionP4AST.BooleanCaseValueExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.BooleanCaseValueExpr n -> new TinyExpressionP4AST.BooleanCaseValueExpr((TinyExpressionP4AST.BooleanOrExpr) convert(n.value()));
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.VariableRefExpr n -> new TinyExpressionP4AST.VariableRefExpr(n.name(), n.type());
            case org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4AST.ExpressionExpr n -> new TinyExpressionP4AST.ExpressionExpr(convertAny(n.value()));
        };
        depth = nodeDepth;
        Span span = sourceSpans.get(node);
        int start = span == null ? Integer.MIN_VALUE : span.start();
        if (span != null) {
            spans.put(converted, new int[] {span.start(), span.end()});
        }
        String name = converted.getClass().getSimpleName();
        int[] rank = bestRank.get(name);
        if (rank == null || nodeDepth < rank[0]
            || (nodeDepth == rank[0] && start >= rank[1])) {
            bestByName.put(name, converted);
            bestRank.put(name, new int[] {nodeDepth, start});
        }
        return converted;
    }
}
