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
  private final java.util.Map<String, TinyExpressionP4AST> methods = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, TinyExpressionP4AST> declarations = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, ExpressionType> declaredVariableTypes = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, String> localBindings = new java.util.LinkedHashMap<>();
  private final java.util.Map<String, ImportTarget> imports = new java.util.LinkedHashMap<>();
  private record ImportTarget(String className, String methodName) {}
  @Override protected String evalQualifiedNameExpr(QualifiedNameExpr node) { return qualifiedName(node); }

  private static String qualifiedName(QualifiedNameExpr node) {
    if (node == null) return "";
    return node.tail().isEmpty() ? node.head() : node.head() + "." + String.join(".", node.tail());
  }

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

  @Override
  protected String evalFormulaExpr(FormulaExpr node) {
    for (ImportDeclarationExpr declaration : node.imports()) {
      eval(declaration);
    }
    for (Object method : node.methods()) {
      if (method instanceof TinyExpressionP4AST ast) {
        eval(ast);
      }
    }
    for (Object declaration : node.declarations()) {
      if (declaration instanceof TinyExpressionP4AST ast) {
        eval(ast);
      }
    }
    return eval(node.expression());
  }

  @Override
  protected String evalNumberVariableDeclarationExpr(NumberVariableDeclarationExpr node) {
    declarations.put(node.varName(), node);
    declaredVariableTypes.put(node.varName(), numberType);
    return "";
  }

  @Override
  protected String evalStringVariableDeclarationExpr(StringVariableDeclarationExpr node) {
    declarations.put(node.varName(), node);
    declaredVariableTypes.put(node.varName(), ExpressionTypes.string);
    return "";
  }

  @Override
  protected String evalBooleanVariableDeclarationExpr(BooleanVariableDeclarationExpr node) {
    declarations.put(node.varName(), node);
    declaredVariableTypes.put(node.varName(), ExpressionTypes._boolean);
    return "";
  }

  @Override
  protected String evalObjectVariableDeclarationExpr(ObjectVariableDeclarationExpr node) {
    declarations.put(node.varName(), node);
    declaredVariableTypes.put(node.varName(), ExpressionTypes.object);
    return "";
  }
  @Override protected String evalArgumentsExpr(ArgumentsExpr n) { return n.values().stream().map(this::eval).collect(java.util.stream.Collectors.joining(", ")); }
  @Override protected String evalNumberMethodDeclarationExpr(NumberMethodDeclarationExpr n) { methods.put(n.methodName(), n); return ""; }
  @Override protected String evalStringMethodDeclarationExpr(StringMethodDeclarationExpr n) { methods.put(n.methodName(), n); return ""; }
  @Override protected String evalBooleanMethodDeclarationExpr(BooleanMethodDeclarationExpr n) { methods.put(n.methodName(), n); return ""; }
  @Override protected String evalObjectMethodDeclarationExpr(ObjectMethodDeclarationExpr n) { methods.put(n.methodName(), n); return ""; }
  @Override protected String evalMethodParametersExpr(MethodParametersExpr n) { return ""; }
  @Override protected String evalMethodParameterExpr(MethodParameterExpr n) { return n.paramName(); }
  @Override protected String evalOnlyIfAbsentExpr(OnlyIfAbsentExpr n) { return "true"; }
  @Override protected String evalStringCastVariableRefExpr(StringCastVariableRefExpr n) { return "calculateContext.getString(\"" + escapeJava(n.name()) + "\").orElse(\"\")"; }
  @Override protected String evalStringTypedVariableRefExpr(StringTypedVariableRefExpr n) { return "calculateContext.getString(\"" + escapeJava(n.name()) + "\").orElse(\"\")"; }
  @Override
  protected String evalBranchExpressionExpr(BranchExpressionExpr node) {
    VariableRefExpr bareVariable = singleVariableRef(node.value());
    if (bareVariable != null) {
      String name = resolveVariableRefName(bareVariable);
      ExpressionType explicit = bareVariable.type().map(this::parseType).orElse(null);
      if (explicit != null) {
        return renderVariableAccess(name, explicit.isNumber() ? numberType : explicit);
      }
      ExpressionType declared = declaredVariableTypes.get(name);
      return renderVariableAccess(name, declared != null ? declared : resultType);
    }
    return node.value() instanceof TinyExpressionP4AST ast ? eval(ast) : String.valueOf(node.value());
  }

  private static VariableRefExpr singleVariableRef(Object value) {
    Object current = value;
    if (current instanceof BooleanOrExpr n && n.op().isEmpty() && n.right().isEmpty()) current = n.left();
    if (current instanceof BooleanAndExpr n && n.op().isEmpty() && n.right().isEmpty()) current = n.left();
    if (current instanceof BooleanXorExpr n && n.op().isEmpty() && n.right().isEmpty()) current = n.left();
    if (current instanceof BooleanFactorExpr n) current = n.value();
    return current instanceof VariableRefExpr variable ? variable : null;
  }
  @Override protected String evalTernaryExpr(TernaryExpr n) { return "(" + eval(n.condition()) + " ? " + eval(n.thenExpr()) + " : " + eval(n.elseExpr()) + ")"; }

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
    // #35: arithmetic is emitted from the AST walk alone (post-#44 mapper maps every
    // operand to a real node). Source-snippet shadow (renderStructuredBinaryNode) removed.
    TinyExpressionP4AST left = node.left();
    List<String> op = node.op();
    List<TinyExpressionP4AST> right = node.right();

    // Leaf: left==null, op=[literal], right=[]
    if (left == null && right.isEmpty() && op.size() == 1) {
      return renderLeafLiteral(op.get(0));
    }
    // Wrap: left!=null, op=[], right=[] — unwrap
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return renderOperand(left);
    }
    if (left == null) {
      if (op.size() == 1) {
        return renderLeafLiteral(op.get(0));
      }
      return "/* unsupported BinaryExpr */0";
    }

    String expr = renderOperand(left);
    int count = Math.min(op.size(), right.size());
    for (int i = 0; i < count; i++) {
      String operator = op.get(i).strip();
      String rightExpr = renderOperand(right.get(i));
      expr = "(" + expr + operator + rightExpr + ")";
    }
    return expr;
  }

  /** Render one arithmetic operand: stay on the BinaryExpr spine, else dispatch the
   *  factor node (AbsExpr, PowExpr, …) so function factors are not dropped. (#43) */
  private String renderOperand(TinyExpressionP4AST operand) {
    return operand instanceof BinaryExpr binary ? evalBinaryExpr(binary) : eval(operand);
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
    String local = localBindings.get(varName);
    if (local != null) {
      return local;
    }
    ExpressionType explicitType = node.type().map(this::parseType).orElse(null);
    if (explicitType != null) {
      ExpressionType resolvedExplicitType = explicitType == ExpressionTypes.number ? numberType : explicitType;
      ExpressionType declaredType = declaredVariableTypes.get(varName);
      return declaredType != null && sameTypeFamily(declaredType, resolvedExplicitType)
          ? renderVariableAccess(varName, declaredType)
          : renderRawVariableAccess(varName, resolvedExplicitType);
    }
    ExpressionType declaredType = declaredVariableTypes.get(varName);
    if (declaredType != null) {
      return renderVariableAccess(varName, declaredType);
    }
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

  private ExpressionType parseType(String text) {
    String normalized = text == null ? "" : text.strip().toLowerCase(java.util.Locale.ROOT);
    return switch (normalized) {
      case "number", "float" -> numberType;
      case "string" -> ExpressionTypes.string;
      case "boolean" -> ExpressionTypes._boolean;
      case "object" -> ExpressionTypes.object;
      default -> null;
    };
  }

  private static boolean sameTypeFamily(ExpressionType left, ExpressionType right) {
    return left.isNumber() && right.isNumber()
        || left.isString() && right.isString()
        || left.isBoolean() && right.isBoolean()
        || left.isObject() && right.isObject();
  }

  private String renderVariableAccess(String varName, ExpressionType type) {
    String local = localBindings.get(varName);
    if (local != null) {
      return local;
    }
    TinyExpressionP4AST declaration = declarations.get(varName);
    if (declaration != null) {
      String declaredAccess = renderDeclaredVariableAccess(varName, declaration);
      if (declaredAccess != null) {
        return declaredAccess;
      }
    }
    return renderRawVariableAccess(varName, type);
  }

  private String renderRawVariableAccess(String varName, ExpressionType type) {
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

  private String renderDeclaredVariableAccess(String varName, TinyExpressionP4AST declaration) {
    String escapedName = escapeJava(varName);
    if (declaration instanceof NumberVariableDeclarationExpr node && node.value().isPresent()) {
      String value = eval(node.value().get());
      if (node.onlyIfAbsent().isPresent()) {
        return numericAccessWithDefault(escapedName, value);
      }
      return "((Number)calculateContext.setAndGet(\"" + escapedName + "\",(Number)(" + value
          + ")))." + primitiveAccessor(numberType) + "()";
    }
    if (declaration instanceof StringVariableDeclarationExpr node && node.value().isPresent()) {
      String value = evalStringConcatExpr(node.value().get());
      if (node.onlyIfAbsent().isPresent()) {
        return "calculateContext.getString(\"" + escapedName + "\").orElse(String.valueOf(" + value + "))";
      }
      return "calculateContext.setAndGet(\"" + escapedName + "\",String.valueOf(" + value + "))";
    }
    if (declaration instanceof BooleanVariableDeclarationExpr node && node.value().isPresent()) {
      String value = evalBooleanOrExpr(node.value().get());
      if (node.onlyIfAbsent().isPresent()) {
        return "calculateContext.getBoolean(\"" + escapedName + "\").orElse((boolean)(" + value + "))";
      }
      return "calculateContext.setAndGet(\"" + escapedName + "\",(boolean)(" + value + "))";
    }
    if (declaration instanceof ObjectVariableDeclarationExpr node && node.value().isPresent()) {
      String value = eval(node.value().get());
      if (node.onlyIfAbsent().isPresent()) {
        return "calculateContext.getObject(\"" + escapedName + "\",Object.class).orElse(" + value + ")";
      }
      return "calculateContext.setAndGetObject(\"" + escapedName + "\"," + value + ",Object.class)";
    }
    return null;
  }

  private String numericAccessWithDefault(String escapedName, String defaultValue) {
    String javaType = numberType.javaTypeAsString();
    if (numberType.isBigDecimal() || numberType.isBigInteger()) {
      return "calculateContext.getNumber(\"" + escapedName + "\").map(v -> (" + javaType
          + ")v).orElse((" + javaType + ")(" + defaultValue + "))";
    }
    return "calculateContext.getNumber(\"" + escapedName + "\").map(Number::"
        + primitiveAccessor(numberType) + ").orElse((" + javaType + ")(" + defaultValue + "))";
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
    List<Object> rights = node.right();
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
    if (value instanceof VariableRefExpr variable) {
      return renderVariableAccess(resolveVariableRefName(variable), ExpressionTypes.string);
    }
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
      return renderVariableAccess(resolveVariableRefName(varRef), ExpressionTypes._boolean);
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

  /**
   * BooleanComparable は透過 mapped choice のため operand は実 AST ノード（Object）または
   * source テキスト（String）として届く。ノードなら直接コード生成、それ以外は従来の
   * source-snippet 経路にフォールバックする。(tinyexpression #32)
   */
  private String renderBooleanOperandSource(Object operand) {
    if (operand instanceof VariableRefExpr variable) {
      return renderVariableAccess(resolveVariableRefName(variable), ExpressionTypes._boolean);
    }
    if (operand instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (operand instanceof String text) {
      return renderBooleanOperandSource(text);
    }
    return "false";
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
    // Close one parenthesis per case plus the outer grouping opened above.
    int caseCount = 1 + moreCases.size();
    sb.append(")".repeat(caseCount + 1));
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
    TinyExpressionP4AST method = methods.get(node.name());
    if (method == null) {
      throw new UnsupportedOperationException("Generated AST method not found: " + node.name());
    }
    List<MethodParameterExpr> parameters = methodParameters(method)
        .map(MethodParametersExpr::values).orElseGet(List::of);
    List<ArgumentExpressionExpr> arguments = node.args()
        .map(ArgumentsExpr::values).orElseGet(List::of);
    if (parameters.size() != arguments.size()) {
      throw new UnsupportedOperationException(
          "Argument count mismatch for generated AST method " + node.name());
    }
    List<String> emittedArguments = arguments.stream().map(this::eval).toList();
    java.util.Map<String, String> previous = new java.util.LinkedHashMap<>(localBindings);
    try {
      for (int i = 0; i < parameters.size(); i++) {
        localBindings.put(parameters.get(i).paramName(), emittedArguments.get(i));
      }
      return eval(methodExpression(method));
    } finally {
      localBindings.clear();
      localBindings.putAll(previous);
    }
  }

  private static java.util.Optional<MethodParametersExpr> methodParameters(TinyExpressionP4AST method) {
    if (method instanceof NumberMethodDeclarationExpr n) return n.parameters();
    if (method instanceof StringMethodDeclarationExpr n) return n.parameters();
    if (method instanceof BooleanMethodDeclarationExpr n) return n.parameters();
    if (method instanceof ObjectMethodDeclarationExpr n) return n.parameters();
    return java.util.Optional.empty();
  }

  private static TinyExpressionP4AST methodExpression(TinyExpressionP4AST method) {
    if (method instanceof NumberMethodDeclarationExpr n) return n.expression();
    if (method instanceof StringMethodDeclarationExpr n) return n.expression();
    if (method instanceof BooleanMethodDeclarationExpr n) return n.expression();
    if (method instanceof ObjectMethodDeclarationExpr n) return n.expression();
    throw new UnsupportedOperationException("Unknown generated AST method: " + method);
  }

  @Override
  protected String evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr node) {
    return emitExternalInvocation(node.className(), node.name(), node.args());
  }

  @Override
  protected String evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr node) {
    return emitExternalInvocation(node.className(), node.name(), node.args());
  }

  @Override
  protected String evalExternalStringInvocationExpr(ExternalStringInvocationExpr node) {
    return emitExternalInvocation(node.className(), node.name(), node.args());
  }

  @Override
  protected String evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr node) {
    return emitExternalInvocation(node.className(), node.name(), node.args());
  }

  private String emitExternalInvocation(QualifiedNameExpr qualifier, String name,
      java.util.Optional<ArgumentsExpr> arguments) {
    String className = qualifiedName(qualifier);
    String methodName = name == null ? "" : name.strip();
    ImportTarget imported = imports.get(methodName);
    if (className.isEmpty() && imported != null) {
      className = imported.className();
      if (imported.methodName() != null && !imported.methodName().isBlank()) {
        methodName = imported.methodName();
      }
    } else if (!className.isEmpty()) {
      ImportTarget classImport = imports.get(className);
      if (classImport != null) {
        className = classImport.className();
      }
    }
    if (className.isEmpty() || methodName.isEmpty()) {
      throw new UnsupportedOperationException("Generated AST external target is incomplete: " + name);
    }
    String args = arguments.map(ArgumentsExpr::values).orElseGet(List::of).stream()
        .map(this::eval).collect(java.util.stream.Collectors.joining(","));
    String separator = args.isEmpty() ? "" : ",";
    String escapedClass = escapeJava(className);
    return "((" + className + ")calculateContext.getObject(\"" + escapedClass
        + "\",Object.class).orElseThrow(()->new org.unlaxer.tinyexpression.CalculationException("
        + "\"class not found in CalculationContext. please set :" + escapedClass + "\")))"
        + "." + methodName + "(calculateContext" + separator + args + ")";
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
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").toUpperCase()";
  }

  @Override
  protected String evalToLowerCaseExpr(ToLowerCaseExpr node) {
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").toLowerCase()";
  }

  @Override
  protected String evalTrimExpr(TrimExpr node) {
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").trim()";
  }

  @Override
  protected String evalLengthExpr(LengthExpr node) {
    return castToNumberType("(double)String.valueOf(" + renderStringLeaf(node.value()) + ").length()");
  }

  // =========================================================================
  // String dot methods (delegate to same logic as function form)
  // =========================================================================

  @Override
  protected String evalToUpperCaseDotExpr(ToUpperCaseDotExpr node) {
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").toUpperCase()";
  }

  @Override
  protected String evalToLowerCaseDotExpr(ToLowerCaseDotExpr node) {
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").toLowerCase()";
  }

  @Override
  protected String evalTrimDotExpr(TrimDotExpr node) {
    return "String.valueOf(" + renderStringLeaf(node.value()) + ").trim()";
  }

  @Override
  protected String evalLengthDotExpr(LengthDotExpr node) {
    return castToNumberType("(double)String.valueOf(" + renderStringLeaf(node.value()) + ").length()");
  }

  // =========================================================================
  // String predicates (function form — boolean-returning)
  // =========================================================================

  @Override
  protected String evalStartsWithExpr(StartsWithExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "startsWith");
  }

  @Override
  protected String evalEndsWithExpr(EndsWithExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "endsWith");
  }

  @Override
  protected String evalContainsExpr(ContainsExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "contains");
  }

  @Override
  protected String evalInExpr(InExpr node) {
    return "org.unlaxer.util.MultipleParamterStringPredicators.in("
        + "String.valueOf(" + renderStringLeaf(node.value()) + ")"
        + renderStringCandidateArguments(node.candidates())
        + ")";
  }

  // =========================================================================
  // String predicates (dot form — boolean-returning)
  // =========================================================================

  @Override
  protected String evalStartsWithDotExpr(StartsWithDotExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "startsWith");
  }

  @Override
  protected String evalEndsWithDotExpr(EndsWithDotExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "endsWith");
  }

  @Override
  protected String evalContainsDotExpr(ContainsDotExpr node) {
    return renderAnyStringPredicate(node.value(), node.patterns(), "contains");
  }

  private String renderAnyStringPredicate(Object value,
      List<StringConcatExpr> patterns, String method) {
    String receiver = "String.valueOf(" + renderStringLeaf(value) + ")";
    return patterns.stream()
        .map(pattern -> receiver + "." + method + "(String.valueOf(" + eval(pattern) + "))")
        .collect(java.util.stream.Collectors.joining(" || ", "(", ")"));
  }

  private String renderCaptured(Object value) {
    return value instanceof TinyExpressionP4AST ast ? eval(ast) : String.valueOf(value);
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
    if (isStringOperand(node.left()) || isStringOperand(node.right())) {
      String left = renderStringLeaf(node.left());
      String right = renderStringLeaf(node.right());
      String op = node.op() == null ? "==" : node.op().strip();
      return "!=".equals(op)
          ? "!String.valueOf(" + left + ").equals(String.valueOf(" + right + "))"
          : "String.valueOf(" + left + ").equals(String.valueOf(" + right + "))";
    }
    String left = renderBooleanOperandSource(node.left());
    String right = renderBooleanOperandSource(node.right());
    String op = node.op() == null ? "==" : node.op().strip();
    return switch (op) {
      case "==" -> "((" + left + ")==(" + right + "))";
      case "!=" -> "((" + left + ")!=(" + right + "))";
      default -> "false";
    };
  }

  private boolean isStringOperand(Object operand) {
    if (operand instanceof StringCastVariableRefExpr || operand instanceof StringTypedVariableRefExpr
        || operand instanceof StringConcatExpr || operand instanceof SliceExpr) {
      return true;
    }
    if (operand instanceof VariableRefExpr variable) {
      ExpressionType explicit = variable.type().map(this::parseType).orElse(null);
      if (explicit != null) {
        return explicit.isString();
      }
      ExpressionType declared = declaredVariableTypes.get(resolveVariableRefName(variable));
      return declared != null && declared.isString();
    }
    return false;
  }

  // =========================================================================
  // String slice (Python-style)
  // =========================================================================

  @Override
  protected String evalSliceExpr(SliceExpr node) {
    String valueExpr = "String.valueOf(" + renderStringLeaf(node.value()) + ")";
    // #35: indices come grammar-disambiguated as integer literals (SliceXxxIndex rules).
    String startExpr = renderSliceIndexExpr(node.start());
    String endExpr = renderSliceIndexExpr(node.end());
    String stepExpr = renderSliceIndexExpr(node.step());
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

  private String renderSliceIndexExpr(String index) {
    if (index == null) {
      return null;
    }
    String stripped = index.strip();
    return stripped.isEmpty() ? null : stripped;
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
    String className = qualifiedName(node.className());
    String alias = node.alias();
    if (alias == null || alias.isBlank()) {
      int dot = className.lastIndexOf('.');
      alias = node.method().orElse(dot < 0 ? className : className.substring(dot + 1));
    }
    imports.put(alias, new ImportTarget(className, node.method().orElse(null)));
    return "";
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
