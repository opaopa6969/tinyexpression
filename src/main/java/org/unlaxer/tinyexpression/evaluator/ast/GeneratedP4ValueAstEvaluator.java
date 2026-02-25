package org.unlaxer.tinyexpression.evaluator.ast;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

final class GeneratedP4ValueAstEvaluator {

  private GeneratedP4ValueAstEvaluator() {}

  static Optional<Object> tryEvaluate(Object mappedAst, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    if (mappedAst == null) {
      return Optional.empty();
    }
    ExpressionType resultType = resolveResultType(specifiedExpressionTypes);
    if (resultType.isNumber()) {
      return GeneratedP4NumberAstEvaluator.tryEvaluate(mappedAst, specifiedExpressionTypes, calculationContext);
    }
    if (resultType.isString()) {
      return findFirstNode(mappedAst, "StringExpr")
          .flatMap(node -> evaluateString(node, calculationContext));
    }
    if (resultType.isBoolean()) {
      return findFirstNode(mappedAst, "BooleanExpr")
          .flatMap(node -> evaluateBoolean(node, calculationContext));
    }
    if (resultType.isObject()) {
      Optional<Object> objectExpr = findFirstNode(mappedAst, "ObjectExpr")
          .flatMap(node -> evaluateObject(node, specifiedExpressionTypes, calculationContext));
      if (objectExpr.isPresent()) {
        return objectExpr;
      }
      Optional<Object> stringExpr = findFirstNode(mappedAst, "StringExpr")
          .flatMap(node -> evaluateString(node, calculationContext))
          .map(value -> (Object) value);
      if (stringExpr.isPresent()) {
        return stringExpr;
      }
      Optional<Object> booleanExpr = findFirstNode(mappedAst, "BooleanExpr")
          .flatMap(node -> evaluateBoolean(node, calculationContext))
          .map(value -> (Object) value);
      if (booleanExpr.isPresent()) {
        return booleanExpr;
      }
      Optional<Object> variableRef = findFirstNode(mappedAst, "VariableRefExpr")
          .flatMap(node -> evaluateVariableRef(node, calculationContext));
      if (variableRef.isPresent()) {
        return variableRef;
      }
      return tryEvaluateBinaryAsObject(mappedAst, specifiedExpressionTypes, calculationContext);
    }
    return Optional.empty();
  }

  private static ExpressionType resolveResultType(SpecifiedExpressionTypes specifiedExpressionTypes) {
    if (specifiedExpressionTypes.resultType() != null) {
      return specifiedExpressionTypes.resultType();
    }
    if (specifiedExpressionTypes.numberType() != null) {
      return specifiedExpressionTypes.numberType();
    }
    return ExpressionTypes.object;
  }

  private static Optional<Object> findFirstNode(Object root, String simpleName) {
    ArrayDeque<Object> queue = new ArrayDeque<>();
    Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    queue.add(root);
    while (!queue.isEmpty()) {
      Object current = queue.removeFirst();
      if (current == null || visited.contains(current)) {
        continue;
      }
      visited.add(current);
      if (simpleName.equals(current.getClass().getSimpleName())) {
        return Optional.of(current);
      }
      for (Object child : reflectiveChildren(current)) {
        if (child != null && isAstNodeCandidate(child)) {
          queue.addLast(child);
        }
      }
    }
    return Optional.empty();
  }

  private static Optional<String> evaluateString(Object node, CalculationContext context) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if ("VariableRefExpr".equals(value.getClass().getSimpleName())) {
      return evaluateVariableRef(value, context).map(String::valueOf);
    }
    String text = String.valueOf(value);
    if (text.startsWith("$")) {
      Optional<Object> fromContext = resolveVariableAny(extractVariableName(text), context);
      if (fromContext.isPresent()) {
        return Optional.of(String.valueOf(fromContext.get()));
      }
    }
    return Optional.of(text);
  }

  private static Optional<Boolean> evaluateBoolean(Object node, CalculationContext context) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if ("VariableRefExpr".equals(value.getClass().getSimpleName())) {
      return evaluateVariableRef(value, context).flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
    }
    String text = String.valueOf(value).strip();
    if (text.startsWith("$")) {
      return resolveVariableAny(extractVariableName(text), context).flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
    }
    return toBoolean(text);
  }

  private static Optional<Object> evaluateObject(Object node, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext context) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    String simpleName = value.getClass().getSimpleName();
    if ("BinaryExpr".equals(simpleName)) {
      return GeneratedP4NumberAstEvaluator.tryEvaluate(value, specifiedExpressionTypes, context);
    }
    if ("StringExpr".equals(simpleName)) {
      return evaluateString(value, context).map(v -> (Object) v);
    }
    if ("BooleanExpr".equals(simpleName)) {
      return evaluateBoolean(value, context).map(v -> (Object) v);
    }
    if ("VariableRefExpr".equals(simpleName)) {
      return evaluateVariableRef(value, context);
    }
    if ("BinaryExpr".equals(node.getClass().getSimpleName())) {
      Optional<Object> binary = tryEvaluateBinaryAsObject(node, specifiedExpressionTypes, context);
      if (binary.isPresent()) {
        return binary;
      }
    }
    return Optional.of(value);
  }

  private static Optional<Object> tryEvaluateBinaryAsObject(Object mappedAst,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context) {
    Optional<Object> binaryNode = "BinaryExpr".equals(mappedAst.getClass().getSimpleName())
        ? Optional.of(mappedAst)
        : findFirstNode(mappedAst, "BinaryExpr");
    if (binaryNode.isEmpty()) {
      return Optional.empty();
    }

    Optional<Object> literalLeaf = tryEvaluateBinaryLeafLiteral(binaryNode.get(), context);
    if (literalLeaf.isPresent()) {
      return literalLeaf;
    }
    return GeneratedP4NumberAstEvaluator.tryEvaluate(binaryNode.get(), specifiedExpressionTypes, context)
        .map(value -> (Object) value);
  }

  private static Optional<Object> tryEvaluateBinaryLeafLiteral(Object binaryNode, CalculationContext context) {
    Object left = invokeZeroArg(binaryNode, "left").orElse(null);
    Object op = invokeZeroArg(binaryNode, "op").orElse(null);
    Object right = invokeZeroArg(binaryNode, "right").orElse(null);
    if (!(op instanceof List<?> opList) || !(right instanceof List<?> rightList)) {
      return Optional.empty();
    }
    if (left != null && rightList.isEmpty() && opList.isEmpty()) {
      return tryEvaluateBinaryLeafLiteral(left, context);
    }
    if (left != null || !rightList.isEmpty() || opList.size() != 1) {
      return Optional.empty();
    }
    String text = String.valueOf(opList.get(0)).strip();
    if (text.startsWith("$")) {
      return resolveVariableAny(extractVariableName(text), context);
    }
    if (text.length() >= 2 && text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\'') {
      return Optional.of(text.substring(1, text.length() - 1));
    }
    if ("true".equalsIgnoreCase(text)) {
      return Optional.of(true);
    }
    if ("false".equalsIgnoreCase(text)) {
      return Optional.of(false);
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateVariableRef(Object variableRefNode, CalculationContext context) {
    Object name = invokeZeroArg(variableRefNode, "name").orElse(null);
    if (name == null) {
      return Optional.empty();
    }
    return resolveVariableAny(extractVariableName(String.valueOf(name)), context);
  }

  private static Optional<Object> resolveVariableAny(String variableName, CalculationContext context) {
    if (variableName == null || variableName.isEmpty()) {
      return Optional.empty();
    }
    Optional<? extends Number> number = context.getNumber(variableName);
    if (number.isPresent()) {
      return Optional.of(number.get());
    }
    Optional<String> string = context.getString(variableName);
    if (string.isPresent()) {
      return Optional.of(string.get());
    }
    Optional<Boolean> bool = context.getBoolean(variableName);
    if (bool.isPresent()) {
      return Optional.of(bool.get());
    }
    Optional<Object> object = context.getObject(variableName, Object.class);
    if (object.isPresent()) {
      return object;
    }
    return Optional.empty();
  }

  private static Optional<Object> invokeZeroArg(Object node, String methodName) {
    try {
      Method method = node.getClass().getMethod(methodName);
      return Optional.ofNullable(method.invoke(node));
    } catch (Throwable ignored) {
      return Optional.empty();
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
          for (Object element : list) {
            if (isAstNodeCandidate(element)) {
              children.add(element);
            }
          }
          continue;
        }
        if (isAstNodeCandidate(value)) {
          children.add(value);
        }
      } catch (Throwable ignored) {
      }
    }
    return children;
  }

  private static boolean isAstNodeCandidate(Object value) {
    if (value == null) {
      return false;
    }
    return value.getClass().getName().contains("TinyExpressionP4AST");
  }

  private static String extractVariableName(String raw) {
    if (raw == null) {
      return null;
    }
    String text = raw.strip();
    if (!text.startsWith("$")) {
      return text;
    }
    int end = 1;
    while (end < text.length()) {
      char c = text.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
        continue;
      }
      break;
    }
    return end > 1 ? text.substring(1, end) : null;
  }

  private static Optional<Boolean> toBoolean(Object value) {
    if (value == null) {
      return Optional.empty();
    }
    if (value instanceof Boolean bool) {
      return Optional.of(bool);
    }
    String text = String.valueOf(value).strip().toLowerCase();
    if ("true".equals(text)) {
      return Optional.of(true);
    }
    if ("false".equals(text)) {
      return Optional.of(false);
    }
    return Optional.empty();
  }
}
