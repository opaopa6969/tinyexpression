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
 * GGP concrete implementation: Java code generation from P4 AST.
 * <p>
 * Extends the generated {@link TinyExpressionP4Evaluator}{@code <String>} base class
 * and implements each {@code evalXxx()} method to emit Java source code strings
 * that can be compiled and executed via javac.
 */
public class P4TypedJavaCodeEmitter extends TinyExpressionP4Evaluator<String> {

  private final ExpressionType resultType;
  private final ExpressionType numberType;
  private final String sourceFormula;

  public P4TypedJavaCodeEmitter(SpecifiedExpressionTypes types) {
    this(types, null);
  }

  public P4TypedJavaCodeEmitter(SpecifiedExpressionTypes types, String sourceFormula) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
    this.sourceFormula = sourceFormula;
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
    String sourceAware = renderStructuredBinaryNode(node);
    if (sourceAware != null) {
      return sourceAware;
    }
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
    String structured = renderStructuredNumberLeaf(literal);
    if (structured != null) {
      return structured;
    }
    // P4 mapper collapses term-level ops (e.g., "3*4") into a single leaf.
    // Pass through as-is if it contains operators — the Java compiler handles it.
    if (literal.contains("*") || literal.contains("/")) {
      return literal;
    }
    return numberType.numberWithSuffix(literal);
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
      return new P4TypedJavaCodeEmitter(
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
  // VariableRefExpr
  // =========================================================================

  @Override
  protected String evalVariableRefExpr(VariableRefExpr node) {
    String varName = resolveVariableRefName(node);
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
  // StringConcatExpr — string concatenation with '+'
  // =========================================================================

  @Override
  protected String evalStringConcatExpr(StringConcatExpr node) {
    String leftExpr = renderStringLeaf(node.left());
    List<String> ops = node.op();
    List<String> rights = node.right();
    if (ops == null || ops.isEmpty()) {
      return leftExpr;
    }
    StringBuilder sb = new StringBuilder("(String.valueOf(").append(leftExpr).append(")");
    int count = Math.min(ops.size(), rights.size());
    for (int i = 0; i < count; i++) {
      sb.append("+String.valueOf(").append(renderStringLeaf(rights.get(i))).append(")");
    }
    sb.append(")");
    return sb.toString();
  }

  private String renderStringLeaf(Object value) {
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        String varName = extractVariableName(stripped);
        return "calculateContext.getString(\"" + escapeJava(varName) + "\").orElse(\"\")";
      }
      String unquoted = unquoteStringLiteral(stripped);
      if (unquoted != null) {
        return "\"" + escapeJava(unquoted) + "\"";
      }
      String structured = renderStructuredStringLeaf(stripped);
      if (structured != null) {
        return structured;
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
    String left = evalStringConcatExpr(node.left());
    String right = evalStringConcatExpr(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> "(" + left + ").equals(" + right + ")";
      case "!=" -> "!(" + left + ").equals(" + right + ")";
      default -> "false";
    };
  }

  private String renderBooleanOperandSource(String rawSource) {
    String normalized = rawSource == null ? "" : rawSource.strip();
    if (normalized.isEmpty()) {
      return "false";
    }
    if (isExactVariableReference(normalized)) {
      return renderVariableAccess(extractVariableName(normalized), ExpressionTypes._boolean);
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
      return new P4TypedJavaCodeEmitter(
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
  protected String evalArgumentExpressionExpr(ArgumentExpressionExpr node) {
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
    return evalStringConcatExpr(node.value());
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
    return castToNumberType("Math.sin(calculateContext.radianAngle(" + eval(node.arg()) + "))");
  }

  @Override
  protected String evalCosExpr(CosExpr node) {
    return castToNumberType("Math.cos(calculateContext.radianAngle(" + eval(node.arg()) + "))");
  }

  @Override
  protected String evalTanExpr(TanExpr node) {
    return castToNumberType("Math.tan(calculateContext.radianAngle(" + eval(node.arg()) + "))");
  }

  @Override
  protected String evalSqrtExpr(SqrtExpr node) {
    return castToNumberType("Math.sqrt((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalMinExpr(MinExpr node) {
    String expr = eval(node.first());
    for (var r : node.rest()) {
      expr = "Math.min((double)" + expr + ",(double)" + eval(r) + ")";
    }
    return castToNumberType(expr);
  }

  @Override
  protected String evalMaxExpr(MaxExpr node) {
    String expr = eval(node.first());
    for (var r : node.rest()) {
      expr = "Math.max((double)" + expr + ",(double)" + eval(r) + ")";
    }
    return castToNumberType(expr);
  }

  @Override
  protected String evalRandomExpr(RandomExpr node) {
    return castToNumberType("Math.random()");
  }

  @Override
  protected String evalAbsExpr(AbsExpr node) {
    return castToNumberType("Math.abs((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalRoundExpr(RoundExpr node) {
    return castToNumberType("(double)Math.round((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalCeilExpr(CeilExpr node) {
    return castToNumberType("Math.ceil((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalFloorExpr(FloorExpr node) {
    return castToNumberType("Math.floor((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalPowExpr(PowExpr node) {
    return castToNumberType("Math.pow((double)" + eval(node.base()) + ",(double)" + eval(node.exponent()) + ")");
  }

  @Override
  protected String evalLogExpr(LogExpr node) {
    return castToNumberType("Math.log((double)" + eval(node.arg()) + ")");
  }

  @Override
  protected String evalExpExpr(ExpExpr node) {
    return castToNumberType("Math.exp((double)" + eval(node.arg()) + ")");
  }

  /** Wraps a double-producing expression with a cast to the result number type (e.g., float). */
  private String castToNumberType(String expression) {
    if (resultType != null && resultType.isFloat()) {
      return "((float) " + expression + ")";
    }
    return expression;
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
    return castToNumberType("(double)String.valueOf(" + eval(node.value()) + ").length()");
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
    return castToNumberType("(double)String.valueOf(" + eval(node.value()) + ").length()");
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
    String varName = resolveVariableRefName(node.value());
    return "calculateContext.isExists(\"" + escapeJava(varName) + "\")";
  }

  // =========================================================================
  // InTimeRange / InDayTimeRange
  // =========================================================================

  @Override
  protected String evalInTimeRangeExpr(InTimeRangeExpr node) {
    String startHour = evalBinaryExpr(node.startHour());
    String endHour = evalBinaryExpr(node.endHour());
    return "org.unlaxer.tinyexpression.function.EmbeddedFunction.inTimeRange(calculateContext,(float)" + startHour + ",(float)" + endHour + ")";
  }

  @Override
  protected String evalInDayTimeRangeExpr(InDayTimeRangeExpr node) {
    String startDay = node.startDay().strip();
    String startHour = evalBinaryExpr(node.startHour());
    String endDay = node.endDay().strip();
    String endHour = evalBinaryExpr(node.endHour());
    return "calculateContext.inDayTimeRange(java.time.DayOfWeek.valueOf(\"" + escapeJava(startDay) + "\"),(float)" + startHour + ",java.time.DayOfWeek.valueOf(\"" + escapeJava(endDay) + "\"),(float)" + endHour + ")";
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
    // Generate inline Java for the slice operation using Slicer
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
      return new P4TypedJavaCodeEmitter(
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
      return renderVariableAccess(extractVariableName(normalized), numberType);
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
      return new P4TypedJavaCodeEmitter(
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

  private String resolveVariableRefName(VariableRefExpr node) {
    if (node == null) {
      return null;
    }
    String rawName = node.name();
    String variableName = extractVariableName(rawName);
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
        String snippetVariableName = extractVariableName(stripped);
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
    String variableName = extractVariableName(raw);
    return variableName != null && ("$" + variableName).equals(raw.strip());
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
