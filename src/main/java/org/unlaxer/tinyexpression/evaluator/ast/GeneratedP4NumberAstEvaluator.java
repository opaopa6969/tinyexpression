package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

final class GeneratedP4NumberAstEvaluator {

  private GeneratedP4NumberAstEvaluator() {}

  static Optional<Object> tryEvaluate(Object mappedAst, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    if (mappedAst == null) {
      return Optional.empty();
    }
    try {
      ExpressionType numberType = resolveNumberType(specifiedExpressionTypes);
      Number value = evalNode(mappedAst, numberType, calculationContext);
      return Optional.of(value);
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  static int countAstNodes(Object node) {
    if (node == null) {
      return 0;
    }
    int count = 1;
    Method[] methods = node.getClass().getMethods();
    for (Method method : methods) {
      if (method.getParameterCount() != 0) {
        continue;
      }
      String name = method.getName();
      if ("getClass".equals(name) || "hashCode".equals(name) || "toString".equals(name)) {
        continue;
      }
      try {
        Object value = method.invoke(node);
        if (value == null) {
          continue;
        }
        if (value instanceof List<?> list) {
          for (Object element : list) {
            if (isAstNodeCandidate(element)) {
              count += countAstNodes(element);
            }
          }
          continue;
        }
        if (isAstNodeCandidate(value)) {
          count += countAstNodes(value);
        }
      } catch (Throwable ignored) {
      }
    }
    return count;
  }

  private static boolean isAstNodeCandidate(Object value) {
    if (value == null) {
      return false;
    }
    String className = value.getClass().getName();
    return className.contains("$") || className.endsWith("AST");
  }

  private static Number evalNode(Object node, ExpressionType numberType, CalculationContext calculationContext)
      throws Exception {
    Method leftMethod = node.getClass().getMethod("left");
    Method opMethod = node.getClass().getMethod("op");
    Method rightMethod = node.getClass().getMethod("right");

    Object leftObj = leftMethod.invoke(node);
    Object opObj = opMethod.invoke(node);
    Object rightObj = rightMethod.invoke(node);

    if (!(opObj instanceof List<?> opList) || !(rightObj instanceof List<?> rightList)) {
      throw new IllegalArgumentException("Unsupported generated AST node shape");
    }

    // Leaf encoding from mapper fallback: left=null, right=[], op=[literal]
    if (leftObj == null && rightList.isEmpty() && opList.size() == 1) {
      String literal = String.valueOf(opList.get(0));
      return numberType.parseNumber(literal);
    }

    Number current;
    if (leftObj == null) {
      if (opList.size() == 1) {
        return numberType.parseNumber(String.valueOf(opList.get(0)));
      }
      throw new IllegalArgumentException("left is null for non-leaf node");
    }
    current = evalNode(leftObj, numberType, calculationContext);

    int count = Math.min(opList.size(), rightList.size());
    for (int i = 0; i < count; i++) {
      String operator = String.valueOf(opList.get(i));
      Number right = evalNode(rightList.get(i), numberType, calculationContext);
      current = applyBinary(operator, current, right, numberType, calculationContext);
    }
    return current;
  }

  private static ExpressionType resolveNumberType(SpecifiedExpressionTypes specifiedExpressionTypes) {
    if (specifiedExpressionTypes.numberType() != null) {
      return specifiedExpressionTypes.numberType();
    }
    if (specifiedExpressionTypes.resultType() != null && specifiedExpressionTypes.resultType().isNumber()) {
      return specifiedExpressionTypes.resultType();
    }
    return ExpressionTypes._float;
  }

  private static Number applyBinary(String operator, Number left, Number right, ExpressionType numberType,
      CalculationContext calculationContext) {
    if (numberType.isBigInteger()) {
      BigInteger l = (BigInteger) left;
      BigInteger r = (BigInteger) right;
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isBigDecimal()) {
      BigDecimal l = (BigDecimal) left;
      BigDecimal r = (BigDecimal) right;
      return switch (operator) {
        case "+" -> l.add(r);
        case "-" -> l.subtract(r);
        case "*" -> l.multiply(r);
        case "/" -> l.divide(r, calculationContext.scale(), calculationContext.roundingMode());
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isInt()) {
      int l = left.intValue();
      int r = right.intValue();
      return switch (operator) {
        case "+" -> l + r;
        case "-" -> l - r;
        case "*" -> l * r;
        case "/" -> l / r;
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isLong()) {
      long l = left.longValue();
      long r = right.longValue();
      return switch (operator) {
        case "+" -> l + r;
        case "-" -> l - r;
        case "*" -> l * r;
        case "/" -> l / r;
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isDouble()) {
      double l = left.doubleValue();
      double r = right.doubleValue();
      return switch (operator) {
        case "+" -> l + r;
        case "-" -> l - r;
        case "*" -> l * r;
        case "/" -> l / r;
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isShort()) {
      int l = left.shortValue();
      int r = right.shortValue();
      return switch (operator) {
        case "+" -> (short) (l + r);
        case "-" -> (short) (l - r);
        case "*" -> (short) (l * r);
        case "/" -> (short) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    if (numberType.isByte()) {
      int l = left.byteValue();
      int r = right.byteValue();
      return switch (operator) {
        case "+" -> (byte) (l + r);
        case "-" -> (byte) (l - r);
        case "*" -> (byte) (l * r);
        case "/" -> (byte) (l / r);
        default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
      };
    }
    float l = left.floatValue();
    float r = right.floatValue();
    return switch (operator) {
      case "+" -> l + r;
      case "-" -> l - r;
      case "*" -> l * r;
      case "/" -> l / r;
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }
}
