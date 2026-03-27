package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * GGP concrete implementation: Java code generation from P4 AST.
 * <p>
 * Extends the generated {@link TinyExpressionP4Evaluator}{@code <String>} base class
 * and implements each {@code evalXxx()} method to emit Java source code strings
 * that can be compiled and executed via javac.
 */
public class P4TypedJavaCodeEmitter extends TinyExpressionP4Evaluator<String> {

  private final ExpressionType resultType;
  private final ExpressionType numberType;

  public P4TypedJavaCodeEmitter(SpecifiedExpressionTypes types) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
  }

  private static ExpressionType resolveNumberType(SpecifiedExpressionTypes types) {
    if (types.numberType() != null) {
      return types.numberType();
    }
    if (types.resultType() != null && types.resultType().isNumber()) {
      return types.resultType();
    }
    return ExpressionTypes._float;
  }

  public String buildJavaClass(String className, String expression) {
    String calculationContextName = "org.unlaxer.tinyexpression.CalculationContext";
    String returnType = resultType.javaTypeAsString();
    return ""
        + "import " + calculationContextName + ";\n"
        + "import org.unlaxer.Token;\n"
        + "\n"
        + "public class " + className + " implements org.unlaxer.tinyexpression.TokenBaseCalculator{\n"
        + "\n"
        + "  @Override\n"
        + "  public " + returnType + " evaluate(" + calculationContextName + " calculateContext , Token token) {\n"
        + "    " + returnType + " answer = (" + returnType + ") \n"
        + "    " + expression + "\n"
        + "    ;\n"
        + "    return answer;\n"
        + "  }\n"
        + "}\n";
  }

  // =========================================================================
  // BinaryExpr — numeric code generation
  // =========================================================================

  @Override
  protected String evalBinaryExpr(BinaryExpr node) {
    BinaryExpr left = node.left();
    List<String> op = node.op();
    List<BinaryExpr> right = node.right();

    // Leaf: left==null, op=[literal], right=[]
    if (left == null && right.isEmpty() && op.size() == 1) {
      return renderLeafLiteral(op.get(0));
    }
    // Wrap: left!=null, op=[], right=[] — unwrap
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return evalBinaryExpr(left);
    }
    if (left == null) {
      if (op.size() == 1) {
        return renderLeafLiteral(op.get(0));
      }
      return "/* unsupported BinaryExpr */0";
    }

    String expr = evalBinaryExpr(left);
    int count = Math.min(op.size(), right.size());
    for (int i = 0; i < count; i++) {
      String operator = op.get(i).strip();
      String rightExpr = evalBinaryExpr(right.get(i));
      expr = "(" + expr + operator + rightExpr + ")";
    }
    return expr;
  }

  private String renderLeafLiteral(String rawLiteral) {
    String literal = rawLiteral == null ? "" : rawLiteral.strip();
    if (literal.startsWith("$")) {
      String varName = extractVariableName(literal);
      return renderVariableAccess(varName, numberType);
    }
    // P4 mapper collapses term-level ops (e.g., "3*4") into a single leaf.
    // Pass through as-is if it contains operators — the Java compiler handles it.
    if (literal.contains("*") || literal.contains("/")) {
      return literal;
    }
    return numberType.numberWithSuffix(literal);
  }

  // =========================================================================
  // VariableRefExpr
  // =========================================================================

  @Override
  protected String evalVariableRefExpr(VariableRefExpr node) {
    String varName = extractVariableName(node.name());
    if (resultType.isNumber()) {
      return renderVariableAccess(varName, numberType);
    }
    if (resultType.isBoolean()) {
      return "calculateContext.getBoolean(\"" + escapeJava(varName) + "\").orElse(false)";
    }
    if (resultType.isString()) {
      return "calculateContext.getString(\"" + escapeJava(varName) + "\").orElse(\"\")";
    }
    return "calculateContext.getObject(\"" + escapeJava(varName) + "\", Object.class).orElse(null)";
  }

  private String renderVariableAccess(String varName, ExpressionType type) {
    if (type.isNumber()) {
      String zero = type.zeroNumber();
      String javaType = type.javaTypeAsString();
      if (type.isBigDecimal() || type.isBigInteger()) {
        return "calculateContext.getNumber(\"" + escapeJava(varName) + "\").map(v -> (" + javaType + ")v).orElse(" + zero + ")";
      }
      return "calculateContext.getNumber(\"" + escapeJava(varName) + "\").map(Number::" + primitiveAccessor(type) + ").orElse((" + javaType + ")" + zero + ")";
    }
    if (type.isBoolean()) {
      return "calculateContext.getBoolean(\"" + escapeJava(varName) + "\").orElse(false)";
    }
    if (type.isString()) {
      return "calculateContext.getString(\"" + escapeJava(varName) + "\").orElse(\"\")";
    }
    return "calculateContext.getObject(\"" + escapeJava(varName) + "\", Object.class).orElse(null)";
  }

  private static String primitiveAccessor(ExpressionType type) {
    if (type.isFloat()) return "floatValue";
    if (type.isDouble()) return "doubleValue";
    if (type.isInt()) return "intValue";
    if (type.isLong()) return "longValue";
    if (type.isShort()) return "shortValue";
    if (type.isByte()) return "byteValue";
    return "floatValue";
  }

  // =========================================================================
  // StringExpr
  // =========================================================================

  @Override
  protected String evalStringExpr(StringExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        String varName = extractVariableName(stripped);
        return "calculateContext.getString(\"" + escapeJava(varName) + "\").orElse(\"\")";
      }
      return "\"" + escapeJava(text) + "\"";
    }
    return "\"\"";
  }

  // =========================================================================
  // BooleanOrExpr / BooleanAndExpr / BooleanXorExpr  (3-level hierarchy)
  // =========================================================================

  @Override
  protected String evalBooleanOrExpr(BooleanOrExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    String current = "(" + eval(node.left()) + ")";
    List<BooleanAndExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      current = "(" + current + " | " + eval(rights.get(i)) + ")";
    }
    return current;
  }

  @Override
  protected String evalBooleanAndExpr(BooleanAndExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    String current = "(" + eval(node.left()) + ")";
    List<BooleanXorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      current = "(" + current + " & " + eval(rights.get(i)) + ")";
    }
    return current;
  }

  @Override
  protected String evalBooleanXorExpr(BooleanXorExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    String current = "(" + eval(node.left()) + ")";
    List<BooleanFactorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      current = "(" + current + " ^ " + eval(rights.get(i)) + ")";
    }
    return current;
  }

  @Override
  protected String evalBooleanFactorExpr(BooleanFactorExpr node) {
    Object value = node.value();
    if (value instanceof ComparisonExpr comp) {
      return eval(comp);
    }
    if (value instanceof StringComparisonExpr scomp) {
      return eval(scomp);
    }
    if (value instanceof VariableRefExpr varRef) {
      return eval(varRef);
    }
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        String varName = extractVariableName(stripped);
        return "calculateContext.getBoolean(\"" + escapeJava(varName) + "\").orElse(false)";
      }
      if ("true".equalsIgnoreCase(stripped)) return "true";
      if ("false".equalsIgnoreCase(stripped)) return "false";
    }
    if (value instanceof Boolean bool) {
      return String.valueOf(bool);
    }
    return "false";
  }

  // =========================================================================
  // ComparisonExpr / StringComparisonExpr
  // =========================================================================

  @Override
  protected String evalComparisonExpr(ComparisonExpr node) {
    String left = evalBinaryExpr(node.left());
    String right = evalBinaryExpr(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    if (numberType.isBigDecimal() || numberType.isBigInteger()) {
      String compareExpr = "new java.math.BigDecimal(String.valueOf(" + left + ")).compareTo(new java.math.BigDecimal(String.valueOf(" + right + ")))";
      return switch (op) {
        case "==" -> "(" + compareExpr + "==0)";
        case "!=" -> "(" + compareExpr + "!=0)";
        case "<"  -> "(" + compareExpr + "<0)";
        case "<=" -> "(" + compareExpr + "<=0)";
        case ">"  -> "(" + compareExpr + ">0)";
        case ">=" -> "(" + compareExpr + ">=0)";
        default -> "false";
      };
    }
    return "(" + left + op + right + ")";
  }

  @Override
  protected String evalStringComparisonExpr(StringComparisonExpr node) {
    String left = evalStringExpr(node.left());
    String right = evalStringExpr(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> "(" + left + ").equals(" + right + ")";
      case "!=" -> "!(" + left + ").equals(" + right + ")";
      default -> "false";
    };
  }

  // =========================================================================
  // IfExpr
  // =========================================================================

  @Override
  protected String evalIfExpr(IfExpr node) {
    String condition = eval(node.condition());
    String thenExpr = eval(node.thenExpr());
    String elseExpr = eval(node.elseExpr());
    return "(((boolean)(" + condition + "))?(" + thenExpr + "):(" + elseExpr + "))";
  }

  // =========================================================================
  // ExpressionExpr
  // =========================================================================

  @Override
  protected String evalExpressionExpr(ExpressionExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        String varName = extractVariableName(stripped);
        return renderVariableAccess(varName, resultType);
      }
    }
    return String.valueOf(value);
  }

  // =========================================================================
  // ObjectExpr
  // =========================================================================

  @Override
  protected String evalObjectExpr(ObjectExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        String varName = extractVariableName(stripped);
        return renderVariableAccess(varName, resultType);
      }
      return "\"" + escapeJava(text) + "\"";
    }
    return "null";
  }

  // =========================================================================
  // Match expressions
  // =========================================================================

  @Override
  protected String evalNumberMatchExpr(NumberMatchExpr node) {
    return renderMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected String evalStringMatchExpr(StringMatchExpr node) {
    return renderMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected String evalBooleanMatchExpr(BooleanMatchExpr node) {
    return renderMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  private <C extends TinyExpressionP4AST, D extends TinyExpressionP4AST>
  String renderMatch(C firstCase, List<C> moreCases, D defaultCase) {
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    appendCaseTernary(sb, firstCase);
    for (C moreCase : moreCases) {
      appendCaseTernary(sb, moreCase);
    }
    sb.append(eval(defaultCase));
    // close parentheses for each case
    int caseCount = 1 + moreCases.size();
    sb.append(")".repeat(caseCount));
    return sb.toString();
  }

  private void appendCaseTernary(StringBuilder sb, TinyExpressionP4AST caseNode) {
    if (caseNode instanceof NumberCaseExpr c) {
      sb.append("((boolean)(").append(eval(c.condition())).append("))?(").append(eval(c.value())).append("):(");
    } else if (caseNode instanceof StringCaseExpr c) {
      sb.append("((boolean)(").append(eval(c.condition())).append("))?(").append(eval(c.value())).append("):(");
    } else if (caseNode instanceof BooleanCaseExpr c) {
      sb.append("((boolean)(").append(eval(c.condition())).append("))?(").append(eval(c.value())).append("):(");
    }
  }

  @Override
  protected String evalNumberCaseExpr(NumberCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalNumberDefaultCaseExpr(NumberDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalNumberCaseValueExpr(NumberCaseValueExpr node) {
    return evalBinaryExpr(node.value());
  }

  @Override
  protected String evalStringCaseExpr(StringCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalStringDefaultCaseExpr(StringDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalStringCaseValueExpr(StringCaseValueExpr node) {
    return evalStringExpr(node.value());
  }

  @Override
  protected String evalBooleanCaseExpr(BooleanCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected String evalBooleanCaseValueExpr(BooleanCaseValueExpr node) {
    return evalBooleanOrExpr(node.value());
  }

  // =========================================================================
  // MethodInvocationExpr / External invocations / Import / CodeBlock
  // =========================================================================

  @Override
  protected String evalMethodInvocationExpr(MethodInvocationExpr node) {
    return "/* unsupported MethodInvocationExpr: " + node.name() + " */null";
  }

  @Override
  protected String evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr node) {
    return "/* unsupported ExternalBooleanInvocation */false";
  }

  @Override
  protected String evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr node) {
    return "/* unsupported ExternalNumberInvocation */" + numberType.zeroNumber();
  }

  @Override
  protected String evalExternalStringInvocationExpr(ExternalStringInvocationExpr node) {
    return "/* unsupported ExternalStringInvocation */\"\"";
  }

  @Override
  protected String evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr node) {
    return "/* unsupported ExternalObjectInvocation */null";
  }

  // =========================================================================
  // Math functions
  // =========================================================================

  @Override
  protected String evalSinExpr(SinExpr node) {
    return "Math.sin((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalCosExpr(CosExpr node) {
    return "Math.cos((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalTanExpr(TanExpr node) {
    return "Math.tan((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalSqrtExpr(SqrtExpr node) {
    return "Math.sqrt((double)" + eval(node.arg()) + ")";
  }

  @Override
  protected String evalMinExpr(MinExpr node) {
    return "Math.min((double)" + eval(node.left()) + ",(double)" + eval(node.right()) + ")";
  }

  @Override
  protected String evalMaxExpr(MaxExpr node) {
    return "Math.max((double)" + eval(node.left()) + ",(double)" + eval(node.right()) + ")";
  }

  @Override
  protected String evalRandomExpr(RandomExpr node) {
    return "Math.random()";
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

  @Override
  protected String evalCodeBlockExpr(CodeBlockExpr node) {
    return "null";
  }

  @Override
  protected String evalImportDeclarationExpr(ImportDeclarationExpr node) {
    return "null";
  }

  // =========================================================================
  // Utility
  // =========================================================================

  private static String extractVariableName(String raw) {
    if (raw == null || raw.isEmpty() || raw.charAt(0) != '$') {
      return null;
    }
    int end = 1;
    while (end < raw.length()) {
      char c = raw.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
      } else {
        break;
      }
    }
    return end > 1 ? raw.substring(1, end) : null;
  }

  private static String escapeJava(String raw) {
    if (raw == null) return "";
    StringBuilder sb = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '"' -> sb.append("\\\"");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }
}
