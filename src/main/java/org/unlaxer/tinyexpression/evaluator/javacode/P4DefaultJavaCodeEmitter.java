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
    String l = evalStringExpr(node.left());
    String r = evalStringExpr(node.right());
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
  protected String evalStringExpr(StringExpr node) {
    Object v = node.value();
    if (v instanceof TinyExpressionP4AST ast) return eval(ast);
    if (v instanceof String t) {
      String s = t.strip();
      if (s.startsWith("$")) return "calculateContext.getString(\"" + esc(stripDollar(s)) + "\").orElse(\"\")";
      return "\"" + esc(t) + "\"";
    }
    return "\"\"";
  }

  @Override
  protected String evalBooleanExpr(BooleanExpr node) {
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
  @Override protected String evalStringCaseValueExpr(StringCaseValueExpr n) { return evalStringExpr(n.value()); }
  @Override protected String evalBooleanCaseExpr(BooleanCaseExpr n) { return eval(n.value()); }
  @Override protected String evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr n) { return eval(n.value()); }
  @Override protected String evalBooleanCaseValueExpr(BooleanCaseValueExpr n) { return evalBooleanExpr(n.value()); }

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
