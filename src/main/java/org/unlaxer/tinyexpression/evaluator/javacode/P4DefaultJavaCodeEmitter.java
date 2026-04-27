package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SliceSourceSupport;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/**
 * Strategy: default — Java code generation from P4 AST.
 * <p>
 * Equivalent to what {@code @eval(strategy=default)} would generate.
 * Each {@code evalXxx()} method produces a Java expression string
 * using standard patterns for each {@code @eval kind}.
 * <p>
 * This class serves as the reference implementation for how
 * EvaluatorGenerator should produce default code.
 */
public class P4DefaultJavaCodeEmitter extends TinyExpressionP4Evaluator<String> {

  private final ExpressionType resultType;
  private final ExpressionType numberType;
  private final String sourceFormula;

  public P4DefaultJavaCodeEmitter(SpecifiedExpressionTypes types) {
    this(types, null);
  }

  public P4DefaultJavaCodeEmitter(SpecifiedExpressionTypes types, String sourceFormula) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
    this.sourceFormula = sourceFormula;
  }

  private static ExpressionType resolveNumberType(SpecifiedExpressionTypes types) {
    if (types.numberType() != null) return types.numberType();
    if (types.resultType() != null && types.resultType().isNumber()) return types.resultType();
    return ExpressionTypes._float;
  }

  public String buildJavaClass(String className, String expression) {
    String ctx = "org.unlaxer.tinyexpression.CalculationContext";
    String ret = resultType.javaTypeAsString();
    return "import " + ctx + ";\nimport org.unlaxer.Token;\n\n"
        + "public class " + className + " implements org.unlaxer.tinyexpression.TokenBaseCalculator{\n\n"
        + "  @Override\n"
        + "  public " + ret + " evaluate(" + ctx + " calculateContext , Token token) {\n"
        + "    " + ret + " answer = (" + ret + ") \n    " + expression + "\n    ;\n"
        + "    return answer;\n  }\n}\n";
  }

  // =========================================================================
  // @eval(kind=binary_arithmetic, strategy=default)
  // =========================================================================

  @Override
  protected String evalBinaryExpr(BinaryExpr node) {
    String sourceAware = renderStructuredBinaryNode(node);
    if (sourceAware != null) {
      return sourceAware;
    }
    BinaryExpr left = node.left();
    List<String> op = node.op();
    List<BinaryExpr> right = node.right();

    if (left == null && right.isEmpty() && op.size() == 1)
      return renderLeaf(op.get(0));
    if (left != null && op.isEmpty() && right.isEmpty())
      return evalBinaryExpr(left);
    if (left == null) {
      return op.size() == 1 ? renderLeaf(op.get(0)) : "0";
    }
    String expr = evalBinaryExpr(left);
    for (int i = 0; i < Math.min(op.size(), right.size()); i++) {
      expr = "(" + expr + op.get(i).strip() + evalBinaryExpr(right.get(i)) + ")";
    }
    return expr;
  }

  private String renderLeaf(String raw) {
    String lit = raw == null ? "" : raw.strip();
    if (lit.startsWith("$")) {
      return renderVarAccess(stripDollar(lit), numberType);
    }
    String structured = renderStructuredNumberLeaf(lit);
    if (structured != null) {
      return structured;
    }
    return numberType.numberWithSuffix(lit);
  }

  private String renderStructuredBinaryNode(BinaryExpr node) {
    if (node == null || sourceFormula == null || sourceFormula.isBlank()) {
      return null;
    }
    return P4SliceSourceSupport.sourceSnippetOfNode(node, sourceFormula)
        .flatMap(this::renderStructuredBinarySourceSnippet)
        .orElse(null);
  }

  private java.util.Optional<String> renderStructuredBinarySourceSnippet(String sourceSnippet) {
    if (sourceSnippet == null) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.ofNullable(renderNumericSourceSnippet(sourceSnippet));
  }

  private boolean hasStructuredNumericAlternative(String text) {
    return !P4PreferredAstMapper.astEvaluatorCandidateAstSimpleNames(text, numberType).isEmpty();
  }

  private String renderStructuredNumberLeaf(String text) {
    if (!looksLikeStructuredNumberLeaf(text)) {
      return null;
    }
    try {
      String normalized = text.strip();
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, numberType).ast();
      return new P4DefaultJavaCodeEmitter(
          new SpecifiedExpressionTypes(numberType, numberType),
          parseSource).eval(ast);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private boolean looksLikeStructuredNumberLeaf(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    String normalized = text.strip();
    if (normalized.isEmpty() || normalized.startsWith("$") || isPlainNumericLiteral(normalized)) {
      return false;
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return looksLikeStructuredNumberLeaf(unwrapped);
    }
    return normalized.startsWith("call ")
        || normalized.startsWith("internal ")
        || normalized.startsWith("external ")
        || normalized.indexOf('(') >= 0
        || normalized.indexOf('[') >= 0
        || normalized.indexOf('*') >= 0
        || normalized.indexOf('/') >= 0
        || normalized.indexOf(',') >= 0
        || normalized.indexOf('?') >= 0;
  }

  private boolean isPlainNumericLiteral(String text) {
    try {
      numberType.parseNumber(text);
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  // =========================================================================
  // @eval(kind=variable_ref, strategy=default, strip_prefix="$")
  // =========================================================================

  @Override
  protected String evalVariableRefExpr(VariableRefExpr node) {
    String name = resolveVariableRefName(node);
    if (resultType.isNumber()) return renderVarAccess(name, numberType);
    if (resultType.isBoolean()) return "calculateContext.getBoolean(\"" + esc(name) + "\").orElse(false)";
    if (resultType.isString()) return "calculateContext.getString(\"" + esc(name) + "\").orElse(\"\")";
    return "calculateContext.getObject(\"" + esc(name) + "\", Object.class).orElse(null)";
  }

  private String renderVarAccess(String name, ExpressionType type) {
    if (type.isBigDecimal() || type.isBigInteger()) {
      return "calculateContext.getNumber(\"" + esc(name) + "\").map(v -> (" + type.javaTypeAsString() + ")v).orElse(" + type.zeroNumber() + ")";
    }
    return "calculateContext.getNumber(\"" + esc(name) + "\").map(Number::" + accessor(type) + ").orElse((" + type.javaTypeAsString() + ")" + type.zeroNumber() + ")";
  }

  // =========================================================================
  // @eval(kind=conditional, strategy=default)
  // =========================================================================

  @Override
  protected String evalIfExpr(IfExpr node) {
    return "(((boolean)(" + eval(node.condition()) + "))?(" + eval(node.thenExpr()) + "):(" + eval(node.elseExpr()) + "))";
  }

  // =========================================================================
  // @eval(kind=comparison, strategy=default)
  // =========================================================================

  @Override
  protected String evalComparisonExpr(ComparisonExpr node) {
    String l = evalBinaryExpr(node.left());
    String r = evalBinaryExpr(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    if (numberType.isBigDecimal() || numberType.isBigInteger()) {
      String cmp = "new java.math.BigDecimal(String.valueOf(" + l + ")).compareTo(new java.math.BigDecimal(String.valueOf(" + r + ")))";
      return switch (op) {
        case "==" -> "(" + cmp + "==0)";
        case "!=" -> "(" + cmp + "!=0)";
        case "<"  -> "(" + cmp + "<0)";
        case "<=" -> "(" + cmp + "<=0)";
        case ">"  -> "(" + cmp + ">0)";
        case ">=" -> "(" + cmp + ">=0)";
        default -> "false";
      };
    }
    return "(" + l + op + r + ")";
  }

  @Override
  protected String evalStringComparisonExpr(StringComparisonExpr node) {
    String l = evalStringConcatExpr(node.left());
    String r = evalStringConcatExpr(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> "(" + l + ").equals(" + r + ")";
      case "!=" -> "!(" + l + ").equals(" + r + ")";
      default -> "false";
    };
  }

  private String renderBooleanOperandSource(String rawSource) {
    String normalized = rawSource == null ? "" : rawSource.strip();
    if (normalized.isEmpty()) {
      return "false";
    }
    if (isExactVariableReference(normalized)) {
      return renderVarAccess(stripDollar(normalized), ExpressionTypes._boolean);
    }
    if ("true".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)) {
      return normalized.toLowerCase(java.util.Locale.ROOT);
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return renderBooleanOperandSource(unwrapped);
    }
    try {
      String parseSource = P4PreferredAstMapper.normalizeExpressionSnippetForParsing(normalized);
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(parseSource, ExpressionTypes._boolean).ast();
      return new P4DefaultJavaCodeEmitter(
          new SpecifiedExpressionTypes(ExpressionTypes._boolean, numberType),
          parseSource).eval(ast);
    } catch (RuntimeException ignored) {
      return "false";
    }
  }

  private String renderStringCandidateArguments(List<StringConcatExpr> candidates) {
    StringBuilder builder = new StringBuilder();
    for (StringConcatExpr candidate : candidates) {
      builder.append(",String.valueOf(").append(eval(candidate)).append(")");
    }
    return builder.toString();
  }

  // =========================================================================
  // @eval(kind=literal, strategy=default)
  // =========================================================================

  @Override
  protected String evalStringConcatExpr(StringConcatExpr node) {
    String leftExpr = renderStringLeaf(node.left());
    List<String> ops = node.op();
    List<String> rights = node.right();
    if (ops == null || ops.isEmpty()) return leftExpr;
    StringBuilder sb = new StringBuilder("(String.valueOf(").append(leftExpr).append(")");
    int count = Math.min(ops.size(), rights.size());
    for (int i = 0; i < count; i++) {
      sb.append("+String.valueOf(").append(renderStringLeaf(rights.get(i))).append(")");
    }
    sb.append(")");
    return sb.toString();
  }

  private String renderStringLeaf(Object v) {
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t) {
      String s = t.strip();
      if (s.startsWith("$")) return "calculateContext.getString(\"" + esc(stripDollar(s)) + "\").orElse(\"\")";
      String unquoted = unquoteStringLiteral(s);
      if (unquoted != null) return "\"" + esc(unquoted) + "\"";
      String structured = renderStructuredStringLeaf(s);
      if (structured != null) return structured;
      return "\"" + esc(t) + "\"";
    }
    return "\"\"";
  }

  @Override
  protected String evalBooleanOrExpr(BooleanOrExpr node) {
    if (node.op() == null || node.op().isEmpty()) return eval(node.left());
    String cur = "(" + eval(node.left()) + ")";
    List<BooleanAndExpr> rights = node.right();
    int n = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < n; i++) cur = "(" + cur + " | " + eval(rights.get(i)) + ")";
    return cur;
  }

  @Override
  protected String evalBooleanAndExpr(BooleanAndExpr node) {
    if (node.op() == null || node.op().isEmpty()) return eval(node.left());
    String cur = "(" + eval(node.left()) + ")";
    List<BooleanXorExpr> rights = node.right();
    int n = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < n; i++) cur = "(" + cur + " & " + eval(rights.get(i)) + ")";
    return cur;
  }

  @Override
  protected String evalBooleanXorExpr(BooleanXorExpr node) {
    if (node.op() == null || node.op().isEmpty()) return eval(node.left());
    String cur = "(" + eval(node.left()) + ")";
    List<BooleanFactorExpr> rights = node.right();
    int n = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < n; i++) cur = "(" + cur + " ^ " + eval(rights.get(i)) + ")";
    return cur;
  }

  @Override
  protected String evalBooleanFactorExpr(BooleanFactorExpr node) {
    Object v = node.value();
    if (v instanceof ComparisonExpr c) return eval(c);
    if (v instanceof StringComparisonExpr c) return eval(c);
    if (v instanceof VariableRefExpr r) return eval(r);
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t) {
      String s = t.strip();
      if (s.startsWith("$")) return "calculateContext.getBoolean(\"" + esc(stripDollar(s)) + "\").orElse(false)";
      if ("true".equalsIgnoreCase(s)) return "true";
      if ("false".equalsIgnoreCase(s)) return "false";
    }
    if (v instanceof Boolean b) return String.valueOf(b);
    return "false";
  }

  // =========================================================================
  // @eval(kind=passthrough, strategy=default)
  // =========================================================================

  @Override
  protected String evalArgumentExpressionExpr(ArgumentExpressionExpr node) {
    Object v = node.value();
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t && t.strip().startsWith("$"))
      return renderVarAccess(stripDollar(t.strip()), resultType);
    return String.valueOf(v);
  }

  @Override
  protected String evalExpressionExpr(ExpressionExpr node) {
    Object v = node.value();
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t && t.strip().startsWith("$"))
      return renderVarAccess(stripDollar(t.strip()), resultType);
    return String.valueOf(v);
  }

  @Override
  protected String evalObjectExpr(ObjectExpr node) {
    Object v = node.value();
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t) {
      String s = t.strip();
      if (s.startsWith("$")) return renderVarAccess(stripDollar(s), resultType);
      return "\"" + esc(t) + "\"";
    }
    return "null";
  }

  // =========================================================================
  // @eval(kind=match_case, strategy=default)
  // =========================================================================

  @Override protected String evalNumberMatchExpr(NumberMatchExpr n) { return renderMatch(n.firstCase(), n.moreCases(), n.defaultCase()); }
  @Override protected String evalStringMatchExpr(StringMatchExpr n) { return renderMatch(n.firstCase(), n.moreCases(), n.defaultCase()); }
  @Override protected String evalBooleanMatchExpr(BooleanMatchExpr n) { return renderMatch(n.firstCase(), n.moreCases(), n.defaultCase()); }

  private <C extends TinyExpressionP4AST, D extends TinyExpressionP4AST>
  String renderMatch(C first, List<C> more, D def) {
    StringBuilder sb = new StringBuilder("(");
    appendCase(sb, first);
    for (C c : more) appendCase(sb, c);
    sb.append(eval(def));
    sb.append(")".repeat(1 + more.size()));
    return sb.toString();
  }

  private void appendCase(StringBuilder sb, TinyExpressionP4AST c) {
    if (c instanceof NumberCaseExpr n) sb.append("((boolean)(").append(eval(n.condition())).append("))?(").append(eval(n.value())).append("):(");
    else if (c instanceof StringCaseExpr n) sb.append("((boolean)(").append(eval(n.condition())).append("))?(").append(eval(n.value())).append("):(");
    else if (c instanceof BooleanCaseExpr n) sb.append("((boolean)(").append(eval(n.condition())).append("))?(").append(eval(n.value())).append("):(");
  }

  @Override protected String evalNumberCaseExpr(NumberCaseExpr n) { return eval(n.value()); }
  @Override protected String evalNumberDefaultCaseExpr(NumberDefaultCaseExpr n) { return eval(n.value()); }
  @Override protected String evalNumberCaseValueExpr(NumberCaseValueExpr n) { return evalBinaryExpr(n.value()); }
  @Override protected String evalStringCaseExpr(StringCaseExpr n) { return eval(n.value()); }
  @Override protected String evalStringDefaultCaseExpr(StringDefaultCaseExpr n) { return eval(n.value()); }
  @Override protected String evalStringCaseValueExpr(StringCaseValueExpr n) { return evalStringConcatExpr(n.value()); }
  @Override protected String evalBooleanCaseExpr(BooleanCaseExpr n) { return eval(n.value()); }
  @Override protected String evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr n) { return eval(n.value()); }
  @Override protected String evalBooleanCaseValueExpr(BooleanCaseValueExpr n) { return evalBooleanOrExpr(n.value()); }

  // =========================================================================
  // @eval(kind=invocation, strategy=manual)
  // =========================================================================

  @Override protected String evalMethodInvocationExpr(MethodInvocationExpr n) { return "/* unsupported MethodInvocation */null"; }
  @Override protected String evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr n) { return "false"; }
  @Override protected String evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr n) { return numberType.zeroNumber(); }
  @Override protected String evalExternalStringInvocationExpr(ExternalStringInvocationExpr n) { return "\"\""; }
  @Override protected String evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr n) { return "null"; }
  @Override protected String evalCodeBlockExpr(CodeBlockExpr n) { return "null"; }
  @Override protected String evalImportDeclarationExpr(ImportDeclarationExpr n) { return "null"; }

  // =========================================================================
  // Math functions
  // =========================================================================

  @Override
  protected String evalSinExpr(SinExpr node) {
    return "Math.sin(calculateContext.radianAngle(" + eval(node.arg()) + "))";
  }

  @Override
  protected String evalCosExpr(CosExpr node) {
    return "Math.cos(calculateContext.radianAngle(" + eval(node.arg()) + "))";
  }

  @Override
  protected String evalTanExpr(TanExpr node) {
    return "Math.tan(calculateContext.radianAngle(" + eval(node.arg()) + "))";
  }

  @Override
  protected String evalSqrtExpr(SqrtExpr node) {
    return "Math.sqrt((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalMinExpr(MinExpr node) {
    String expr = eval(node.first());
    for (var r : node.rest()) {
      expr = "Math.min((double)" + expr + ",(double)" + eval(r) + ")";
    }
    return expr;
  }

  @Override
  protected String evalMaxExpr(MaxExpr node) {
    String expr = eval(node.first());
    for (var r : node.rest()) {
      expr = "Math.max((double)" + expr + ",(double)" + eval(r) + ")";
    }
    return expr;
  }

  @Override
  protected String evalRandomExpr(RandomExpr node) {
    return "Math.random()";
  }

  @Override
  protected String evalAbsExpr(AbsExpr node) {
    return "Math.abs((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalRoundExpr(RoundExpr node) {
    return "(double)Math.round((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalCeilExpr(CeilExpr node) {
    return "Math.ceil((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalFloorExpr(FloorExpr node) {
    return "Math.floor((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalPowExpr(PowExpr node) {
    return "Math.pow((double)" + eval(node.base()) + ",(double)" + eval(node.exponent()) + ")";
  }

  @Override
  protected String evalLogExpr(LogExpr node) {
    return "Math.log((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalExpExpr(ExpExpr node) {
    return "Math.exp((double)" + eval(node.arg()) + ")";
  }

  // =========================================================================
  // String methods (function form)
  // =========================================================================

  @Override
  protected String evalToUpperCaseExpr(ToUpperCaseExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").toUpperCase()";
  }

  @Override
  protected String evalToLowerCaseExpr(ToLowerCaseExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").toLowerCase()";
  }

  @Override
  protected String evalTrimExpr(TrimExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").trim()";
  }

  @Override
  protected String evalLengthExpr(LengthExpr node) {
    return "(double)String.valueOf(" + eval(node.value()) + ").length()";
  }

  // =========================================================================
  // String dot methods (delegate to same logic as function form)
  // =========================================================================

  @Override
  protected String evalToUpperCaseDotExpr(ToUpperCaseDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").toUpperCase()";
  }

  @Override
  protected String evalToLowerCaseDotExpr(ToLowerCaseDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").toLowerCase()";
  }

  @Override
  protected String evalTrimDotExpr(TrimDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").trim()";
  }

  @Override
  protected String evalLengthDotExpr(LengthDotExpr node) {
    return "(double)String.valueOf(" + eval(node.value()) + ").length()";
  }

  // =========================================================================
  // String predicates (function form — boolean-returning)
  // =========================================================================

  @Override
  protected String evalStartsWithExpr(StartsWithExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").startsWith(String.valueOf(" + eval(node.pattern()) + "))";
  }

  @Override
  protected String evalEndsWithExpr(EndsWithExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").endsWith(String.valueOf(" + eval(node.pattern()) + "))";
  }

  @Override
  protected String evalContainsExpr(ContainsExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").contains(String.valueOf(" + eval(node.pattern()) + "))";
  }

  @Override
  protected String evalInExpr(InExpr node) {
    return "org.unlaxer.util.MultipleParamterStringPredicators.in("
        + "String.valueOf(" + eval(node.value()) + ")"
        + renderStringCandidateArguments(node.candidates())
        + ")";
  }

  // =========================================================================
  // String predicates (dot form — boolean-returning)
  // =========================================================================

  @Override
  protected String evalStartsWithDotExpr(StartsWithDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").startsWith(String.valueOf(" + eval(node.pattern()) + "))";
  }

  @Override
  protected String evalEndsWithDotExpr(EndsWithDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").endsWith(String.valueOf(" + eval(node.pattern()) + "))";
  }

  @Override
  protected String evalContainsDotExpr(ContainsDotExpr node) {
    return "String.valueOf(" + eval(node.value()) + ").contains(String.valueOf(" + eval(node.pattern()) + "))";
  }

  // =========================================================================
  // isPresent
  // =========================================================================

  @Override
  protected String evalIsPresentExpr(IsPresentExpr node) {
    String name = resolveVariableRefName(node.value());
    return "calculateContext.isExists(\"" + esc(name) + "\")";
  }

  // =========================================================================
  // InTimeRange / InDayTimeRange
  // =========================================================================

  @Override
  protected String evalInTimeRangeExpr(InTimeRangeExpr node) {
    return "org.unlaxer.tinyexpression.function.EmbeddedFunction.inTimeRange(calculateContext,(float)" + evalBinaryExpr(node.startHour()) + ",(float)" + evalBinaryExpr(node.endHour()) + ")";
  }

  @Override
  protected String evalInDayTimeRangeExpr(InDayTimeRangeExpr node) {
    String startDay = node.startDay().strip();
    String endDay = node.endDay().strip();
    return "calculateContext.inDayTimeRange(java.time.DayOfWeek.valueOf(\"" + esc(startDay) + "\"),(float)" + evalBinaryExpr(node.startHour()) + ",java.time.DayOfWeek.valueOf(\"" + esc(endDay) + "\"),(float)" + evalBinaryExpr(node.endHour()) + ")";
  }

  // =========================================================================
  // Not operator
  // =========================================================================

  @Override
  protected String evalNotExpr(NotExpr node) {
    return "!((boolean)(" + eval(node.value()) + "))";
  }

  @Override
  protected String evalBooleanEqualityExpr(BooleanEqualityExpr node) {
    String left = renderBooleanOperandSource(node.left());
    String right = renderBooleanOperandSource(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> "((" + left + ")==(" + right + "))";
      case "!=" -> "((" + left + ")!=(" + right + "))";
      default -> "false";
    };
  }

  // =========================================================================
  // ToNum conversion
  // =========================================================================

  @Override
  protected String evalToNumExpr(ToNumExpr node) {
    String strExpr = eval(node.value());
    String defExpr = eval(node.defaultValue());
    return "((() -> { try { return Double.parseDouble(String.valueOf(" + strExpr + ")); } catch (NumberFormatException e) { return (double)" + defExpr + "; } }).get())";
  }

  // =========================================================================
  // String slice (Python-style)
  // =========================================================================

  @Override
  protected String evalSliceExpr(SliceExpr node) {
    String valueExpr = "String.valueOf(" + renderStringLeaf(node.value()) + ")";
    P4SliceSourceSupport.SliceParts sliceParts =
        P4SliceSourceSupport.slicePartsOfNode(node, sourceFormula).orElse(null);
    boolean sourceAware = sliceParts != null;
    String startExpr = renderSliceIndexExpr(node.start(), sourceAware, sliceParts == null ? null : sliceParts.startSource());
    String endExpr = renderSliceIndexExpr(node.end(), sourceAware, sliceParts == null ? null : sliceParts.endSource());
    String stepExpr = renderSliceIndexExpr(node.step(), sourceAware, sliceParts == null ? null : sliceParts.stepSource());
    StringBuilder sb = new StringBuilder();
    sb.append("new org.unlaxer.util.Slicer(org.unlaxer.StringSource.createRootSource(")
        .append(valueExpr).append("))");
    if (startExpr != null) {
      sb.append(".begin(new org.unlaxer.CodePointIndex((int)").append(startExpr).append("))");
    }
    if (endExpr != null) {
      sb.append(".end(new org.unlaxer.CodePointIndex((int)").append(endExpr).append("))");
    }
    if (stepExpr != null) {
      sb.append(".step((int)").append(stepExpr).append(")");
    }
    sb.append(".get().sourceAsString()");
    return sb.toString();
  }

  private String renderStructuredStringLeaf(String text) {
    if (!looksLikeStructuredStringLeaf(text)) {
      return null;
    }
    try {
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(text, ExpressionTypes.string).ast();
      return new P4DefaultJavaCodeEmitter(
          new SpecifiedExpressionTypes(ExpressionTypes.string, numberType),
          text).eval(ast);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String renderSliceIndexExpr(BinaryExpr astNode, boolean sourceAware, String sourceSnippet) {
    if (sourceAware) {
      if (sourceSnippet == null) {
        return null;
      }
      String rendered = renderNumericSourceSnippet(sourceSnippet);
      if (rendered != null) {
        return rendered;
      }
    }
    return astNode != null ? evalBinaryExpr(astNode) : null;
  }

  private String renderNumericSourceSnippet(String sourceSnippet) {
    if (sourceSnippet == null) {
      return null;
    }
    String normalized = TinyExpressionParserCapabilities
        .stripJavaStyleCommentsPreservingLayout(sourceSnippet)
        .strip();
    if (normalized.isEmpty()) {
      return null;
    }
    if (isExactVariableReference(normalized)) {
      return renderVarAccess(stripDollar(normalized), numberType);
    }
    if (isPlainNumericLiteral(normalized)) {
      return numberType.numberWithSuffix(normalized);
    }
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      String inner = renderNumericSourceSnippet(unwrapped);
      if (inner != null) {
        return "(" + inner + ")";
      }
    }
    ArithmeticSplit addSub = splitTopLevelArithmetic(normalized, false);
    if (addSub != null) {
      String left = renderNumericSourceSnippet(addSub.left());
      String right = renderNumericSourceSnippet(addSub.right());
      if (left != null && right != null) {
        return "(" + left + addSub.operator() + right + ")";
      }
    }
    ArithmeticSplit mulDiv = splitTopLevelArithmetic(normalized, true);
    if (mulDiv != null) {
      String left = renderNumericSourceSnippet(mulDiv.left());
      String right = renderNumericSourceSnippet(mulDiv.right());
      if (left != null && right != null) {
        return "(" + left + mulDiv.operator() + right + ")";
      }
    }
    try {
      TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(normalized, numberType).ast();
      if (ast instanceof ExpressionExpr expression && expression.value() instanceof TinyExpressionP4AST innerAst) {
        ast = innerAst;
      }
      if (ast instanceof BinaryExpr) {
        return null;
      }
      return new P4DefaultJavaCodeEmitter(
          new SpecifiedExpressionTypes(numberType, numberType),
          normalized).eval(ast);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private record ArithmeticSplit(String left, String operator, String right) {}

  private static ArithmeticSplit splitTopLevelArithmetic(String text, boolean mulDivOnly) {
    if (text == null || text.isBlank()) {
      return null;
    }
    int parenDepth = 0;
    int bracketDepth = 0;
    int braceDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    int splitIndex = -1;
    char splitOperator = '\0';
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        default -> {
        }
      }
      if (parenDepth != 0 || bracketDepth != 0 || braceDepth != 0) {
        continue;
      }
      if (mulDivOnly) {
        if (c == '*' || c == '/') {
          splitIndex = i;
          splitOperator = c;
        }
        continue;
      }
      if ((c == '+' || c == '-') && !isUnaryNumericOperator(text, i)) {
        splitIndex = i;
        splitOperator = c;
      }
    }
    if (splitIndex <= 0 || splitIndex >= text.length() - 1) {
      return null;
    }
    String left = text.substring(0, splitIndex).strip();
    String right = text.substring(splitIndex + 1).strip();
    if (left.isEmpty() || right.isEmpty()) {
      return null;
    }
    return new ArithmeticSplit(left, String.valueOf(splitOperator), right);
  }

  private static boolean isUnaryNumericOperator(String text, int index) {
    if (index < 0 || index >= text.length()) {
      return false;
    }
    char operator = text.charAt(index);
    if (operator != '+' && operator != '-') {
      return false;
    }
    int prev = index - 1;
    while (prev >= 0 && Character.isWhitespace(text.charAt(prev))) {
      prev--;
    }
    if (prev < 0) {
      return true;
    }
    char previous = text.charAt(prev);
    return previous == '('
        || previous == '['
        || previous == '{'
        || previous == ','
        || previous == ':'
        || previous == '?'
        || previous == '+'
        || previous == '-'
        || previous == '*'
        || previous == '/';
  }

  private static boolean looksLikeStructuredStringLeaf(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    String normalized = text.strip();
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return looksLikeStructuredStringLeaf(unwrapped)
          || hasTopLevelStringConcat(unwrapped);
    }
    return normalized.startsWith("trim(")
        || normalized.startsWith("toUpperCase(")
        || normalized.startsWith("toLowerCase(")
        || normalized.startsWith("call ")
        || normalized.startsWith("internal ")
        || normalized.startsWith("external ")
        || normalized.contains(".trim(")
        || normalized.contains(".toUpperCase(")
        || normalized.contains(".toLowerCase(")
        || (normalized.indexOf('[') >= 0 && normalized.endsWith("]"));
  }

  private static String unwrapWholeParentheses(String text) {
    String current = text;
    while (isWrappedByWholeParentheses(current)) {
      current = current.substring(1, current.length() - 1).strip();
    }
    return current;
  }

  private static boolean isWrappedByWholeParentheses(String text) {
    if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
      return false;
    }
    int parenDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> {
          parenDepth--;
          if (parenDepth == 0 && i < text.length() - 1) {
            return false;
          }
        }
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (parenDepth < 0 || bracketDepth < 0) {
        return false;
      }
    }
    return parenDepth == 0 && bracketDepth == 0;
  }

  private static boolean hasTopLevelStringConcat(String text) {
    int parenDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '+' -> {
          if (parenDepth == 0 && bracketDepth == 0) {
            return true;
          }
        }
        default -> {
        }
      }
    }
    return false;
  }

  private static String unquoteStringLiteral(String text) {
    if (text == null || text.length() < 2) {
      return null;
    }
    char start = text.charAt(0);
    char end = text.charAt(text.length() - 1);
    if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
      for (int i = 1; i < text.length() - 1; i++) {
        if (text.charAt(i) == start && text.charAt(i - 1) != '\\') {
          return null;
        }
      }
      return text.substring(1, text.length() - 1);
    }
    return null;
  }

  // =========================================================================
  // Utility
  // =========================================================================

  private static String stripDollar(String s) {
    if (s == null || !s.startsWith("$")) return s;
    int end = 1;
    while (end < s.length() && (Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_')) end++;
    return end > 1 ? s.substring(1, end) : s;
  }

  private String resolveVariableRefName(VariableRefExpr node) {
    if (node == null) {
      return null;
    }
    String rawName = node.name();
    String variableName = stripDollar(rawName);
    if ((variableName == null || variableName.isEmpty())
        && rawName != null
        && !rawName.isBlank()
        && !rawName.startsWith("$")) {
      variableName = rawName.strip();
    }
    if ((variableName == null || variableName.isEmpty())
        && sourceFormula != null
        && !sourceFormula.isBlank()) {
      java.util.Optional<String> snippet = P4SliceSourceSupport.sourceSnippetOfNode(node, sourceFormula);
      if (snippet.isPresent()) {
        String stripped = snippet.get().strip();
        String snippetVariableName = stripDollar(stripped);
        if (snippetVariableName != null && !snippetVariableName.isEmpty()) {
          return snippetVariableName;
        }
        if (!stripped.isEmpty() && !stripped.startsWith("$")) {
          return stripped;
        }
      }
    }
    return variableName;
  }

  private static boolean isExactVariableReference(String raw) {
    String stripped = raw == null ? "" : raw.strip();
    if (!stripped.startsWith("$")) {
      return false;
    }
    String variableName = stripDollar(stripped);
    return variableName != null && !variableName.isEmpty() && ("$" + variableName).equals(stripped);
  }

  private static String accessor(ExpressionType t) {
    if (t.isFloat()) return "floatValue";
    if (t.isDouble()) return "doubleValue";
    if (t.isInt()) return "intValue";
    if (t.isLong()) return "longValue";
    if (t.isShort()) return "shortValue";
    if (t.isByte()) return "byteValue";
    return "floatValue";
  }

  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }
}
