package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
      Object evalTarget = findBinaryExprLikeNode(mappedAst).orElse(mappedAst);
      Number value = evalNode(evalTarget, numberType, calculationContext);
      return Optional.of(value);
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private static Optional<Object> findBinaryExprLikeNode(Object root) {
    ArrayDeque<Object> queue = new ArrayDeque<>();
    Set<Object> visited = new HashSet<>();
    queue.add(root);
    while (!queue.isEmpty()) {
      Object current = queue.removeFirst();
      if (current == null || visited.contains(current)) {
        continue;
      }
      visited.add(current);
      if (hasBinaryShape(current)) {
        return Optional.of(current);
      }
      for (Object child : reflectiveChildren(current)) {
        if (child != null) {
          queue.addLast(child);
        }
      }
    }
    return Optional.empty();
  }

  private static boolean hasBinaryShape(Object node) {
    try {
      node.getClass().getMethod("left");
      node.getClass().getMethod("op");
      node.getClass().getMethod("right");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static List<Object> reflectiveChildren(Object node) {
    java.util.ArrayList<Object> children = new java.util.ArrayList<>();
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
          children.addAll(list);
          continue;
        }
        children.add(value);
      } catch (Throwable ignored) {
      }
    }
    return children;
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
      return resolveLeafLiteral(literal, numberType, calculationContext);
    }

    Number current;
    if (leftObj == null) {
      if (opList.size() == 1) {
        return resolveLeafLiteral(String.valueOf(opList.get(0)), numberType, calculationContext);
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

  private static Number resolveLeafLiteral(String rawLiteral, ExpressionType numberType,
      CalculationContext calculationContext) {
    String literal = rawLiteral == null ? "" : rawLiteral.strip();
    if (literal.startsWith("$")) {
      String variableName = extractVariableName(literal);
      if (variableName != null && !variableName.isEmpty()) {
        Optional<? extends Number> number = calculationContext.getNumber(variableName);
        if (number.isPresent()) {
          return number.get();
        }
        Optional<Float> value = calculationContext.getValue(variableName);
        if (value.isPresent()) {
          return value.get();
        }
      }
    }
    return numberType.parseNumber(literal);
  }

  private static String extractVariableName(String literal) {
    if (literal == null || literal.isEmpty() || literal.charAt(0) != '$') {
      return null;
    }
    int end = 1;
    while (end < literal.length()) {
      char c = literal.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
        continue;
      }
      break;
    }
    if (end <= 1) {
      return null;
    }
    return literal.substring(1, end);
  }
}
