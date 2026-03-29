package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

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

  public P4DefaultJavaCodeEmitter(SpecifiedExpressionTypes types) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
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
    return numberType.numberWithSuffix(lit);
  }

  // =========================================================================
  // @eval(kind=variable_ref, strategy=default, strip_prefix="$")
  // =========================================================================

  @Override
  protected String evalVariableRefExpr(VariableRefExpr node) {
    String name = stripDollar(node.name());
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
    return "calculateContext.isExists(\"" + node.value().name() + "\")";
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
    String valueExpr = "String.valueOf(" + eval(node.value()) + ")";
    String startExpr = node.start().isPresent() ? evalBinaryExpr(node.start().get()) : null;
    String endExpr = node.end().isPresent() ? evalBinaryExpr(node.end().get()) : null;
    String stepExpr = node.step().isPresent() ? evalBinaryExpr(node.step().get()) : null;
    StringBuilder sb = new StringBuilder();
    sb.append("new org.unlaxer.util.Slicer(new org.unlaxer.StringSource(").append(valueExpr).append("))");
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

  // =========================================================================
  // Utility
  // =========================================================================

  private static String stripDollar(String s) {
    if (s == null || !s.startsWith("$")) return s;
    int end = 1;
    while (end < s.length() && (Character.isLetterOrDigit(s.charAt(end)) || s.charAt(end) == '_')) end++;
    return end > 1 ? s.substring(1, end) : s;
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
