package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Evaluator;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * GGP concrete implementation: AST traversal evaluator.
 * <p>
 * Extends the generated {@link TinyExpressionP4Evaluator}{@code <Object>} base class
 * and implements each {@code evalXxx()} method to evaluate the sealed P4 AST nodes
 * directly — no reflection needed.
 */
public class P4TypedAstEvaluator extends TinyExpressionP4Evaluator<Object> {

  private final ExpressionType resultType;
  private final ExpressionType numberType;
  private final CalculationContext context;

  public P4TypedAstEvaluator(SpecifiedExpressionTypes types, CalculationContext context) {
    this.resultType = types.resultType() != null ? types.resultType() : ExpressionTypes.object;
    this.numberType = resolveNumberType(types);
    this.context = context;
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

  // =========================================================================
  // BinaryExpr — numeric arithmetic
  // =========================================================================

  @Override
  protected Object evalBinaryExpr(BinaryExpr node) {
    return evalBinaryAsNumber(node);
  }

  private Number evalBinaryAsNumber(BinaryExpr node) {
    BinaryExpr left = node.left();
    List<String> op = node.op();
    List<BinaryExpr> right = node.right();

    // Leaf: left==null, op=[literal], right=[]
    if (left == null && right.isEmpty() && op.size() == 1) {
      return resolveLeafLiteral(op.get(0));
    }
    // Wrap: left!=null, op=[], right=[] — unwrap
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return evalBinaryAsNumber(left);
    }
    if (left == null) {
      if (op.size() == 1) {
        return resolveLeafLiteral(op.get(0));
      }
      throw new IllegalArgumentException("left is null for non-leaf BinaryExpr");
    }

    Number current = evalBinaryAsNumber(left);
    int count = Math.min(op.size(), right.size());
    for (int i = 0; i < count; i++) {
      Number r = evalBinaryAsNumber(right.get(i));
      current = applyBinary(op.get(i), current, r);
    }
    return current;
  }

  private Number resolveLeafLiteral(String rawLiteral) {
    String literal = rawLiteral == null ? "" : rawLiteral.strip();
    if (literal.startsWith("$")) {
      String varName = extractVariableName(literal);
      if (varName != null) {
        Optional<? extends Number> number = context.getNumber(varName);
        if (number.isPresent()) {
          return number.get();
        }
      }
    }
    // P4 mapper collapses term-level ops (e.g., "3*4") into a single leaf.
    // Evaluate simple term expressions manually.
    if (literal.contains("*") || literal.contains("/")) {
      return evaluateCollapsedTerm(literal);
    }
    return numberType.parseNumber(literal);
  }

  private Number evaluateCollapsedTerm(String term) {
    // Split by * and / while preserving operator order
    List<String> tokens = new java.util.ArrayList<>();
    List<Character> ops = new java.util.ArrayList<>();
    int start = 0;
    for (int i = 0; i < term.length(); i++) {
      char c = term.charAt(i);
      if ((c == '*' || c == '/') && i > start) {
        tokens.add(term.substring(start, i).strip());
        ops.add(c);
        start = i + 1;
      }
    }
    tokens.add(term.substring(start).strip());
    Number result = numberType.parseNumber(tokens.get(0));
    for (int i = 0; i < ops.size(); i++) {
      Number right = numberType.parseNumber(tokens.get(i + 1));
      result = applyBinary(String.valueOf(ops.get(i)), result, right);
    }
    return result;
  }

  private Number applyBinary(String operator, Number left, Number right) {
    if (numberType.isBigInteger()) {
      BigInteger l = (left instanceof BigInteger bi) ? bi : BigInteger.valueOf(left.longValue());
      BigInteger r = (right instanceof BigInteger bi) ? bi : BigInteger.valueOf(right.longValue());
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isBigDecimal()) {
      BigDecimal l = toBigDecimal(left);
      BigDecimal r = toBigDecimal(right);
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r, context.scale(), context.roundingMode());
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isInt()) {
      return switch (operator) {
        case "+" -> left.intValue() + right.intValue();
        case "-" -> left.intValue() - right.intValue();
        case "*" -> left.intValue() * right.intValue();
        case "/" -> left.intValue() / right.intValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isLong()) {
      return switch (operator) {
        case "+" -> left.longValue() + right.longValue();
        case "-" -> left.longValue() - right.longValue();
        case "*" -> left.longValue() * right.longValue();
        case "/" -> left.longValue() / right.longValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isDouble()) {
      return switch (operator) {
        case "+" -> left.doubleValue() + right.doubleValue();
        case "-" -> left.doubleValue() - right.doubleValue();
        case "*" -> left.doubleValue() * right.doubleValue();
        case "/" -> left.doubleValue() / right.doubleValue();
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isShort()) {
      int l = left.shortValue(), r = right.shortValue();
      return switch (operator) {
        case "+" -> (short) (l + r);
        case "-" -> (short) (l - r);
        case "*" -> (short) (l * r);
        case "/" -> (short) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isByte()) {
      int l = left.byteValue(), r = right.byteValue();
      return switch (operator) {
        case "+" -> (byte) (l + r);
        case "-" -> (byte) (l - r);
        case "*" -> (byte) (l * r);
        case "/" -> (byte) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    // default: float
    return switch (operator) {
      case "+" -> left.floatValue() + right.floatValue();
      case "-" -> left.floatValue() - right.floatValue();
      case "*" -> left.floatValue() * right.floatValue();
      case "/" -> left.floatValue() / right.floatValue();
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }

  // =========================================================================
  // VariableRefExpr
  // =========================================================================

  @Override
  protected Object evalVariableRefExpr(VariableRefExpr node) {
    String rawName = node.name();
    String varName = extractVariableName(rawName);
    // VariableRefExpr may store the name without '$' prefix
    if (varName == null && rawName != null && !rawName.isEmpty()) {
      varName = rawName.strip();
    }
    if (varName == null) {
      return null;
    }
    if (resultType.isNumber()) {
      Object result = context.getNumber(varName).orElse(null);
      if (result != null) return result;
    }
    if (resultType.isBoolean()) {
      Object result = context.getBoolean(varName).orElse(null);
      if (result != null) return result;
    }
    if (resultType.isString()) {
      Object result = context.getString(varName).orElse(null);
      if (result != null) return result;
    }
    // Fallback: try all types (handles cross-type contexts like boolean vars in number expressions)
    return resolveVariableAny(varName);
  }

  private Object resolveVariableAny(String varName) {
    Optional<? extends Number> number = context.getNumber(varName);
    if (number.isPresent()) return number.get();
    Optional<String> string = context.getString(varName);
    if (string.isPresent()) return string.get();
    Optional<Boolean> bool = context.getBoolean(varName);
    if (bool.isPresent()) return bool.get();
    return context.getObject(varName, Object.class).orElse(null);
  }

  // =========================================================================
  // StringExpr
  // =========================================================================

  @Override
  protected Object evalStringExpr(StringExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        Object resolved = resolveVariableAny(extractVariableName(stripped));
        if (resolved != null) return String.valueOf(resolved);
      }
      return text;
    }
    return value == null ? null : String.valueOf(value);
  }

  // =========================================================================
  // BooleanOrExpr / BooleanAndExpr / BooleanXorExpr  (3-level hierarchy)
  // =========================================================================

  @Override
  protected Object evalBooleanOrExpr(BooleanOrExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanAndExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current | r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanAndExpr(BooleanAndExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanXorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current & r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanXorExpr(BooleanXorExpr node) {
    if (node.op() == null || node.op().isEmpty()) {
      return eval(node.left());
    }
    boolean current = toBoolean(eval(node.left()));
    List<BooleanFactorExpr> rights = node.right();
    int count = Math.min(node.op().size(), rights.size());
    for (int i = 0; i < count; i++) {
      boolean r = toBoolean(eval(rights.get(i)));
      current = current ^ r;
    }
    return current;
  }

  @Override
  protected Object evalBooleanFactorExpr(BooleanFactorExpr node) {
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
        if (varName == null || varName.isEmpty()) {
          throw new UnsupportedOperationException(
              "Cannot resolve boolean variable from incomplete reference: " + stripped);
        }
        Object resolved = resolveVariableAny(varName);
        return toBoolean(resolved);
      }
      return toBoolean(text);
    }
    if (value instanceof Boolean bool) {
      return bool;
    }
    return toBoolean(value);
  }

  // =========================================================================
  // ComparisonExpr / StringComparisonExpr
  // =========================================================================

  @Override
  protected Object evalComparisonExpr(ComparisonExpr node) {
    Number left = evalBinaryAsNumber(node.left());
    Number right = evalBinaryAsNumber(node.right());
    String op = node.op() == null ? "" : node.op().strip();
    int compare = toBigDecimal(left).compareTo(toBigDecimal(right));
    return switch (op) {
      case "==" -> compare == 0;
      case "!=" -> compare != 0;
      case "<"  -> compare < 0;
      case "<=" -> compare <= 0;
      case ">"  -> compare > 0;
      case ">=" -> compare >= 0;
      default -> false;
    };
  }

  @Override
  protected Object evalStringComparisonExpr(StringComparisonExpr node) {
    String left = String.valueOf(evalStringExpr(node.left()));
    String right = String.valueOf(evalStringExpr(node.right()));
    String op = node.op() == null ? "" : node.op().strip();
    return switch (op) {
      case "==" -> left.equals(right);
      case "!=" -> !left.equals(right);
      default -> false;
    };
  }

  // =========================================================================
  // IfExpr
  // =========================================================================

  @Override
  protected Object evalIfExpr(IfExpr node) {
    Object conditionValue = eval(node.condition());
    boolean cond = Boolean.TRUE.equals(toBoolean(conditionValue));
    ExpressionExpr branch = cond ? node.thenExpr() : node.elseExpr();
    return eval(branch);
  }

  // =========================================================================
  // ExpressionExpr — unwrap
  // =========================================================================

  @Override
  protected Object evalExpressionExpr(ExpressionExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        return resolveVariableAny(extractVariableName(stripped));
      }
    }
    return value;
  }

  // =========================================================================
  // ObjectExpr
  // =========================================================================

  @Override
  protected Object evalObjectExpr(ObjectExpr node) {
    Object value = node.value();
    if (value instanceof TinyExpressionP4AST ast) {
      return eval(ast);
    }
    if (value instanceof String text) {
      String stripped = text.strip();
      if (stripped.startsWith("$")) {
        return resolveVariableAny(extractVariableName(stripped));
      }
    }
    return value;
  }

  // =========================================================================
  // Match expressions (Number/String/Boolean)
  // =========================================================================

  @Override
  protected Object evalNumberMatchExpr(NumberMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected Object evalStringMatchExpr(StringMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  @Override
  protected Object evalBooleanMatchExpr(BooleanMatchExpr node) {
    return evaluateMatch(node.firstCase(), node.moreCases(), node.defaultCase());
  }

  private <C extends TinyExpressionP4AST, D extends TinyExpressionP4AST>
  Object evaluateMatch(C firstCase, List<C> moreCases, D defaultCase) {
    Object result = tryEvalCase(firstCase);
    if (result != null) return result;
    for (C moreCase : moreCases) {
      result = tryEvalCase(moreCase);
      if (result != null) return result;
    }
    return eval(defaultCase);
  }

  private Object tryEvalCase(TinyExpressionP4AST caseNode) {
    // All case nodes have condition() and value()
    if (caseNode instanceof NumberCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    } else if (caseNode instanceof StringCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    } else if (caseNode instanceof BooleanCaseExpr c) {
      if (Boolean.TRUE.equals(toBoolean(eval(c.condition())))) return eval(c.value());
    }
    return null;
  }

  @Override
  protected Object evalNumberCaseExpr(NumberCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalNumberDefaultCaseExpr(NumberDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalNumberCaseValueExpr(NumberCaseValueExpr node) {
    return evalBinaryAsNumber(node.value());
  }

  @Override
  protected Object evalStringCaseExpr(StringCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalStringDefaultCaseExpr(StringDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalStringCaseValueExpr(StringCaseValueExpr node) {
    return evalStringExpr(node.value());
  }

  @Override
  protected Object evalBooleanCaseExpr(BooleanCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalBooleanDefaultCaseExpr(BooleanDefaultCaseExpr node) {
    return eval(node.value());
  }

  @Override
  protected Object evalBooleanCaseValueExpr(BooleanCaseValueExpr node) {
    return evalBooleanOrExpr(node.value());
  }

  // =========================================================================
  // MethodInvocationExpr / External invocations / Import / CodeBlock
  // =========================================================================

  @Override
  protected Object evalMethodInvocationExpr(MethodInvocationExpr node) {
    throw new UnsupportedOperationException("MethodInvocationExpr not yet supported in P4TypedAstEvaluator");
  }

  @Override
  protected Object evalExternalBooleanInvocationExpr(ExternalBooleanInvocationExpr node) {
    throw new UnsupportedOperationException("ExternalBooleanInvocationExpr not yet supported");
  }

  @Override
  protected Object evalExternalNumberInvocationExpr(ExternalNumberInvocationExpr node) {
    throw new UnsupportedOperationException("ExternalNumberInvocationExpr not yet supported");
  }

  @Override
  protected Object evalExternalStringInvocationExpr(ExternalStringInvocationExpr node) {
    throw new UnsupportedOperationException("ExternalStringInvocationExpr not yet supported");
  }

  @Override
  protected Object evalExternalObjectInvocationExpr(ExternalObjectInvocationExpr node) {
    throw new UnsupportedOperationException("ExternalObjectInvocationExpr not yet supported");
  }

  // =========================================================================
  // Math functions
  // =========================================================================

  @Override
  protected Object evalSinExpr(SinExpr node) {
    return Math.sin(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalCosExpr(CosExpr node) {
    return Math.cos(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalTanExpr(TanExpr node) {
    return Math.tan(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalSqrtExpr(SqrtExpr node) {
    return Math.sqrt(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalMinExpr(MinExpr node) {
    double min = ((Number) eval(node.first())).doubleValue();
    for (var r : node.rest()) {
      min = Math.min(min, ((Number) eval(r)).doubleValue());
    }
    return min;
  }

  @Override
  protected Object evalMaxExpr(MaxExpr node) {
    double max = ((Number) eval(node.first())).doubleValue();
    for (var r : node.rest()) {
      max = Math.max(max, ((Number) eval(r)).doubleValue());
    }
    return max;
  }

  @Override
  protected Object evalRandomExpr(RandomExpr node) {
    return Math.random();
  }

  @Override
  protected Object evalAbsExpr(AbsExpr node) {
    return Math.abs(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalRoundExpr(RoundExpr node) {
    return (double) Math.round(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalCeilExpr(CeilExpr node) {
    return Math.ceil(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalFloorExpr(FloorExpr node) {
    return Math.floor(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalPowExpr(PowExpr node) {
    double base = ((Number) eval(node.base())).doubleValue();
    double exponent = ((Number) eval(node.exponent())).doubleValue();
    return Math.pow(base, exponent);
  }

  @Override
  protected Object evalLogExpr(LogExpr node) {
    return Math.log(((Number) eval(node.arg())).doubleValue());
  }

  @Override
  protected Object evalExpExpr(ExpExpr node) {
    return Math.exp(((Number) eval(node.arg())).doubleValue());
  }

  // =========================================================================
  // Not operator
  // =========================================================================

  @Override
  protected Object evalNotExpr(NotExpr node) {
    return !Boolean.TRUE.equals(toBoolean(eval(node.value())));
  }

  // =========================================================================
  // ToNum conversion
  // =========================================================================

  @Override
  protected Object evalToNumExpr(ToNumExpr node) {
    Object strVal = eval(node.value());
    try {
      return Double.parseDouble(String.valueOf(strVal));
    } catch (NumberFormatException e) {
      return ((Number) eval(node.defaultValue())).doubleValue();
    }
  }

  @Override
  protected Object evalCodeBlockExpr(CodeBlockExpr node) {
    return null;
  }

  @Override
  protected Object evalImportDeclarationExpr(ImportDeclarationExpr node) {
    return null;
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

  private static Boolean toBoolean(Object value) {
    if (value instanceof Boolean bool) return bool;
    if (value == null) return false;
    String text = String.valueOf(value).strip().toLowerCase();
    if ("true".equals(text)) return true;
    if ("false".equals(text)) return false;
    return false;
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) return bd;
    return new BigDecimal(String.valueOf(value));
  }
}
