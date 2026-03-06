package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionKeywords;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

final class GeneratedP4ValueAstEvaluator {

  private static final ThreadLocal<Boolean> EMBEDDED_BRIDGE_USED =
      ThreadLocal.withInitial(() -> false);

  private GeneratedP4ValueAstEvaluator() {}

  static void resetEmbeddedBridgeUsageFlag() {
    EMBEDDED_BRIDGE_USED.set(false);
  }

  static boolean consumeEmbeddedBridgeUsageFlag() {
    boolean used = EMBEDDED_BRIDGE_USED.get();
    EMBEDDED_BRIDGE_USED.set(false);
    return used;
  }

  static Optional<Object> tryEvaluate(Object mappedAst, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    return tryEvaluate(mappedAst, specifiedExpressionTypes, calculationContext, null, null);
  }

  static Optional<Object> tryEvaluate(Object mappedAst, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext, ClassLoader classLoader, String fallbackFormulaSource) {
    if (mappedAst == null) {
      return Optional.empty();
    }
    ExpressionType resultType = resolveResultType(specifiedExpressionTypes);
    String rootSimpleName = mappedAst.getClass().getSimpleName();
    if ("ExpressionExpr".equals(rootSimpleName)) {
      Object unwrapped = unwrapExpressionNode(mappedAst);
      if (unwrapped == null || unwrapped == mappedAst) {
        return Optional.empty();
      }
      return tryEvaluate(unwrapped, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
    }
    if (resultType.isNumber()) {
      if ("IfExpr".equals(rootSimpleName)) {
        Optional<Object> ifValue = evaluateIfExpression(
            mappedAst, resultType, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
        if (ifValue.isPresent() && ifValue.get() instanceof Number) {
          return ifValue;
        }
      }
      if ("NumberMatchExpr".equals(rootSimpleName)) {
        Optional<Object> matchValue = evaluateNumberMatchExpression(
            mappedAst, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
        if (matchValue.isPresent()) {
          return matchValue;
        }
      }
      if ("MethodInvocationExpr".equals(rootSimpleName)) {
        Optional<Object> invocation = evaluateMethodInvocation(mappedAst, resultType,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
        if (invocation.isPresent() && invocation.get() instanceof Number) {
          return invocation;
        }
      }
      Optional<Object> ifExpression = findFirstNode(mappedAst, "IfExpr").flatMap(node -> evaluateIfExpression(
          node, resultType, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (ifExpression.isPresent() && ifExpression.get() instanceof Number) {
        return ifExpression;
      }
      Optional<Object> numberMatch = findFirstNode(mappedAst, "NumberMatchExpr")
          .flatMap(node -> evaluateNumberMatchExpression(
              node, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (numberMatch.isPresent()) {
        return numberMatch;
      }
      Optional<Object> number = GeneratedP4NumberAstEvaluator.tryEvaluate(
          mappedAst, specifiedExpressionTypes, calculationContext);
      if (number.isPresent()) {
        return number;
      }
      return evaluateEmbedded(
          fallbackFormulaSource, resultType, specifiedExpressionTypes, calculationContext, classLoader, null);
    }
    if (resultType.isString()) {
      if ("IfExpr".equals(rootSimpleName)) {
        return evaluateIfExpression(mappedAst, ExpressionTypes.string,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource)
                .map(String::valueOf)
                .map(v -> (Object) v);
      }
      if ("StringMatchExpr".equals(rootSimpleName)) {
        return evaluateStringMatchExpression(
            mappedAst, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource)
                .map(v -> (Object) v);
      }
      if ("MethodInvocationExpr".equals(rootSimpleName)) {
        return evaluateMethodInvocation(mappedAst, ExpressionTypes.string,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource)
                .map(String::valueOf)
                .map(v -> (Object) v);
      }
      Optional<Object> ifExpression = findFirstNode(mappedAst, "IfExpr").flatMap(node -> evaluateIfExpression(
          node, ExpressionTypes.string, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (ifExpression.isPresent()) {
        return ifExpression.map(String::valueOf).map(v -> (Object) v);
      }
      Optional<Object> stringMatch = findFirstNode(mappedAst, "StringMatchExpr")
          .flatMap(node -> evaluateStringMatchExpression(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
      if (stringMatch.isPresent()) {
        return stringMatch;
      }
      return findFirstNode(mappedAst, "StringExpr")
          .flatMap(node -> evaluateString(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
    }
    if (resultType.isBoolean()) {
      if ("IfExpr".equals(rootSimpleName)) {
        return evaluateIfExpression(mappedAst, ExpressionTypes._boolean,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource)
                .flatMap(GeneratedP4ValueAstEvaluator::toBoolean)
                .map(v -> (Object) v);
      }
      if ("BooleanMatchExpr".equals(rootSimpleName)) {
        return evaluateBooleanMatchExpression(
            mappedAst, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource)
                .map(v -> (Object) v);
      }
      if ("MethodInvocationExpr".equals(rootSimpleName)) {
        return evaluateMethodInvocation(mappedAst, ExpressionTypes._boolean,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource)
                .flatMap(GeneratedP4ValueAstEvaluator::toBoolean)
                .map(v -> (Object) v);
      }
      Optional<Object> ifExpression = findFirstNode(mappedAst, "IfExpr").flatMap(node -> evaluateIfExpression(
          node, ExpressionTypes._boolean, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (ifExpression.isPresent()) {
        return ifExpression.flatMap(GeneratedP4ValueAstEvaluator::toBoolean).map(v -> (Object) v);
      }
      Optional<Object> booleanMatch = findFirstNode(mappedAst, "BooleanMatchExpr")
          .flatMap(node -> evaluateBooleanMatchExpression(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
      if (booleanMatch.isPresent()) {
        return booleanMatch;
      }
      return findFirstNode(mappedAst, "BooleanExpr")
          .flatMap(node -> evaluateBoolean(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
    }
    if (resultType.isObject()) {
      if ("IfExpr".equals(rootSimpleName)) {
        Optional<Object> ifValue = evaluateIfExpression(
            mappedAst, ExpressionTypes.object, specifiedExpressionTypes, calculationContext, classLoader,
            fallbackFormulaSource);
        if (ifValue.isPresent()) {
          return ifValue;
        }
      }
      if ("NumberMatchExpr".equals(rootSimpleName)) {
        Optional<Object> match = evaluateNumberMatchExpression(
            mappedAst, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
        if (match.isPresent()) {
          return match;
        }
      }
      if ("StringMatchExpr".equals(rootSimpleName)) {
        Optional<String> match = evaluateStringMatchExpression(
            mappedAst, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
        if (match.isPresent()) {
          return match.map(v -> (Object) v);
        }
      }
      if ("BooleanMatchExpr".equals(rootSimpleName)) {
        Optional<Boolean> match = evaluateBooleanMatchExpression(
            mappedAst, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
        if (match.isPresent()) {
          return match.map(v -> (Object) v);
        }
      }
      if ("MethodInvocationExpr".equals(rootSimpleName)) {
        Optional<Object> invocation = evaluateMethodInvocation(mappedAst, ExpressionTypes.object,
            specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource);
        if (invocation.isPresent()) {
          return invocation;
        }
      }
      Optional<Object> ifExpression = findFirstNode(mappedAst, "IfExpr").flatMap(node -> evaluateIfExpression(
          node, ExpressionTypes.object, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (ifExpression.isPresent()) {
        return ifExpression;
      }
      Optional<Object> numberMatch = findFirstNode(mappedAst, "NumberMatchExpr")
          .flatMap(node -> evaluateNumberMatchExpression(
              node, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (numberMatch.isPresent()) {
        return numberMatch;
      }
      Optional<Object> stringMatch = findFirstNode(mappedAst, "StringMatchExpr")
          .flatMap(node -> evaluateStringMatchExpression(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
      if (stringMatch.isPresent()) {
        return stringMatch;
      }
      Optional<Object> booleanMatch = findFirstNode(mappedAst, "BooleanMatchExpr")
          .flatMap(node -> evaluateBooleanMatchExpression(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(v -> (Object) v);
      if (booleanMatch.isPresent()) {
        return booleanMatch;
      }
      Optional<Object> objectExpr = findFirstNode(mappedAst, "ObjectExpr")
          .flatMap(node -> evaluateObject(
              node, specifiedExpressionTypes, calculationContext, classLoader, fallbackFormulaSource));
      if (objectExpr.isPresent()) {
        return objectExpr;
      }
      Optional<Object> stringExpr = findFirstNode(mappedAst, "StringExpr")
          .flatMap(node -> evaluateString(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
          .map(value -> (Object) value);
      if (stringExpr.isPresent()) {
        return stringExpr;
      }
      Optional<Object> booleanExpr = findFirstNode(mappedAst, "BooleanExpr")
          .flatMap(node -> evaluateBoolean(
              node, calculationContext, specifiedExpressionTypes, classLoader, fallbackFormulaSource))
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

  private static Object unwrapExpressionNode(Object node) {
    Object current = node;
    while (current != null && "ExpressionExpr".equals(current.getClass().getSimpleName())) {
      Object value = invokeZeroArg(current, "value").orElse(null);
      if (value == null || value == current) {
        return null;
      }
      current = value;
    }
    return current;
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

  private static Optional<String> evaluateString(Object node, CalculationContext context,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader, String fallbackFormulaSource) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if ("VariableRefExpr".equals(value.getClass().getSimpleName())) {
      return evaluateVariableRef(value, context).map(String::valueOf);
    }
    if ("MethodInvocationExpr".equals(value.getClass().getSimpleName())) {
      Optional<Object> invocation = evaluateMethodInvocation(value, ExpressionTypes.string,
          specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      return invocation.map(String::valueOf);
    }
    String text = String.valueOf(value).strip();
    if (text.startsWith("$")) {
      Optional<Object> fromContext = resolveVariableAny(extractVariableName(text), context);
      if (fromContext.isPresent()) {
        return Optional.of(String.valueOf(fromContext.get()));
      }
    }
    Optional<Object> remapped = tryEvaluateStructuredTextViaAst(
        text, ExpressionTypes.string, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (remapped.isPresent()) {
      return Optional.of(String.valueOf(remapped.get()));
    }
    Optional<Object> embedded = evaluateEmbedded(
        text, ExpressionTypes.string, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (embedded.isPresent()) {
      return Optional.of(String.valueOf(embedded.get()));
    }
    if (AstEmbeddedExpressionRuntime.isLikelyStructuredExpression(text)) {
      return Optional.empty();
    }
    return Optional.of(text);
  }

  private static Optional<Boolean> evaluateBoolean(Object node, CalculationContext context,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader, String fallbackFormulaSource) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    if ("VariableRefExpr".equals(value.getClass().getSimpleName())) {
      return evaluateVariableRef(value, context).flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
    }
    if ("MethodInvocationExpr".equals(value.getClass().getSimpleName())) {
      return evaluateMethodInvocation(value, ExpressionTypes._boolean,
          specifiedExpressionTypes, context, classLoader, fallbackFormulaSource)
              .flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
    }
    if ("ComparisonExpr".equals(value.getClass().getSimpleName())) {
      return evaluateComparison(value, specifiedExpressionTypes, context);
    }
    String text = String.valueOf(value).strip();
    if (text.startsWith("$")) {
      return resolveVariableAny(extractVariableName(text), context).flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
    }
    Optional<Boolean> literal = toBoolean(text);
    if (literal.isPresent()) {
      return literal;
    }
    Optional<Object> remapped = tryEvaluateStructuredTextViaAst(
        text, ExpressionTypes._boolean, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (remapped.isPresent()) {
      return toBoolean(remapped.get());
    }
    Optional<Object> embedded = evaluateEmbedded(
        text, ExpressionTypes._boolean, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (embedded.isPresent()) {
      return toBoolean(embedded.get());
    }
    return Optional.empty();
  }

  private static Optional<Object> evaluateObject(Object node, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext context, ClassLoader classLoader, String fallbackFormulaSource) {
    Object value = invokeZeroArg(node, "value").orElse(null);
    if (value == null) {
      return Optional.empty();
    }
    String simpleName = value.getClass().getSimpleName();
    if ("BinaryExpr".equals(simpleName)) {
      return GeneratedP4NumberAstEvaluator.tryEvaluate(value, specifiedExpressionTypes, context);
    }
    if ("StringExpr".equals(simpleName)) {
      return evaluateString(value, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource)
          .map(v -> (Object) v);
    }
    if ("BooleanExpr".equals(simpleName)) {
      return evaluateBoolean(value, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource)
          .map(v -> (Object) v);
    }
    if ("VariableRefExpr".equals(simpleName)) {
      return evaluateVariableRef(value, context);
    }
    if ("MethodInvocationExpr".equals(simpleName)) {
      return evaluateMethodInvocation(value, ExpressionTypes.object,
          specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    }
    if ("ComparisonExpr".equals(simpleName)) {
      return evaluateComparison(value, specifiedExpressionTypes, context).map(v -> (Object) v);
    }
    if ("BinaryExpr".equals(node.getClass().getSimpleName())) {
      Optional<Object> binary = tryEvaluateBinaryAsObject(node, specifiedExpressionTypes, context);
      if (binary.isPresent()) {
        return binary;
      }
    }
    if (value instanceof String text) {
      String normalized = text.strip();
      if (normalized.startsWith("$")) {
        Optional<Object> variable = resolveVariableAny(extractVariableName(normalized), context);
        if (variable.isPresent()) {
          return variable;
        }
      }
      Optional<Object> remapped = tryEvaluateStructuredTextViaAst(
          normalized, ExpressionTypes.object, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      if (remapped.isPresent()) {
        return remapped;
      }
      Optional<Object> embedded = evaluateEmbedded(
          normalized, ExpressionTypes.object, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      if (embedded.isPresent()) {
        return embedded;
      }
      if (AstEmbeddedExpressionRuntime.isLikelyStructuredExpression(normalized)) {
        return Optional.empty();
      }
      return Optional.of(normalized);
    }
    return Optional.of(value);
  }

  private static Optional<Object> tryEvaluateStructuredTextViaAst(String sourceText, ExpressionType expectedType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String fallbackFormulaSource) {
    String text = sourceText == null ? "" : sourceText.strip();
    if (text.isEmpty() || !AstEmbeddedExpressionRuntime.isLikelyStructuredExpression(text)) {
      return Optional.empty();
    }
    String fallback = fallbackFormulaSource == null ? "" : fallbackFormulaSource.strip();
    if (!fallback.isEmpty() && fallback.equals(text)) {
      return Optional.empty();
    }
    ClassLoader effectiveClassLoader =
        classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
        expectedType, resolveNumberTypeForEvaluation(expectedType, specifiedExpressionTypes.numberType()));
    for (String preferredAstSimpleName : preferredAstSimpleNames(expectedType, text)) {
      Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
          text, effectiveClassLoader, preferredAstSimpleName);
      if (mapped.isEmpty()) {
        continue;
      }
      Optional<Object> evaluated = tryEvaluate(mapped.get(), evalTypes, context, effectiveClassLoader, text);
      if (evaluated.isPresent()) {
        return evaluated;
      }
    }
    return Optional.empty();
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

  private static Optional<Boolean> evaluateComparison(Object comparisonNode,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context) {
    Object leftNode = invokeZeroArg(comparisonNode, "left").orElse(null);
    Object rightNode = invokeZeroArg(comparisonNode, "right").orElse(null);
    Object opNode = invokeZeroArg(comparisonNode, "op").orElse(null);
    if (leftNode == null || rightNode == null || opNode == null) {
      return Optional.empty();
    }
    Optional<Object> leftValue = GeneratedP4NumberAstEvaluator.tryEvaluate(leftNode, specifiedExpressionTypes, context)
        .map(v -> (Object) v);
    Optional<Object> rightValue = GeneratedP4NumberAstEvaluator.tryEvaluate(rightNode, specifiedExpressionTypes, context)
        .map(v -> (Object) v);
    if (leftValue.isEmpty() || rightValue.isEmpty()) {
      return Optional.empty();
    }
    if (!(leftValue.get() instanceof Number leftNumber) || !(rightValue.get() instanceof Number rightNumber)) {
      return Optional.empty();
    }
    String op = normalizeComparisonOperator(opNode);
    if (op == null || op.isEmpty()) {
      return Optional.empty();
    }
    int compare = toBigDecimal(leftNumber).compareTo(toBigDecimal(rightNumber));
    return switch (op) {
      case "==" -> Optional.of(compare == 0);
      case "!=" -> Optional.of(compare != 0);
      case "<" -> Optional.of(compare < 0);
      case "<=" -> Optional.of(compare <= 0);
      case ">" -> Optional.of(compare > 0);
      case ">=" -> Optional.of(compare >= 0);
      default -> Optional.empty();
    };
  }

  private static Optional<Object> evaluateEmbedded(String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String fallbackFormulaSource) {
    Optional<Object> embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
        expressionSource, resultType, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (embedded.isPresent()) {
      EMBEDDED_BRIDGE_USED.set(true);
    }
    return embedded;
  }

  private static Optional<Object> evaluateIfExpression(Object ifNode, ExpressionType expectedType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String fallbackFormulaSource) {
    Object conditionNode = invokeZeroArg(ifNode, "condition").orElse(null);
    Object thenNode = invokeZeroArg(ifNode, "thenExpr").orElse(null);
    Object elseNode = invokeZeroArg(ifNode, "elseExpr").orElse(null);
    if (conditionNode == null || thenNode == null || elseNode == null) {
      return Optional.empty();
    }
    Optional<Boolean> condition = evaluateNodeAsBoolean(
        conditionNode, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
    if (condition.isEmpty()) {
      return Optional.empty();
    }
    Object selected = condition.get() ? thenNode : elseNode;
    Object branchNode = unwrapExpressionNode(selected);
    if (branchNode == null) {
      return Optional.empty();
    }
    SpecifiedExpressionTypes branchTypes = new SpecifiedExpressionTypes(
        expectedType, resolveNumberTypeForEvaluation(expectedType, specifiedExpressionTypes.numberType()));
    return tryEvaluate(branchNode, branchTypes, context, classLoader, fallbackFormulaSource);
  }

  private static Optional<Boolean> evaluateNodeAsBoolean(Object node, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext context, ClassLoader classLoader, String fallbackFormulaSource) {
    if (node == null) {
      return Optional.empty();
    }
    if ("BooleanExpr".equals(node.getClass().getSimpleName())) {
      return evaluateBoolean(node, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
    }
    SpecifiedExpressionTypes booleanTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, specifiedExpressionTypes.numberType());
    return tryEvaluate(node, booleanTypes, context, classLoader, fallbackFormulaSource)
        .flatMap(GeneratedP4ValueAstEvaluator::toBoolean);
  }

  private static Optional<Object> evaluateNumberMatchExpression(Object matchNode,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String fallbackFormulaSource) {
    Object firstCaseNode = invokeZeroArg(matchNode, "firstCase").orElse(null);
    Object moreCasesNode = invokeZeroArg(matchNode, "moreCases").orElse(null);
    Object defaultNode = invokeZeroArg(matchNode, "defaultCase").orElse(null);
    if (defaultNode == null) {
      return Optional.empty();
    }
    List<Object> cases = mergeCaseNodes(firstCaseNode, moreCasesNode);
    for (Object caseNode : cases) {
      if (caseNode == null) {
        continue;
      }
      Object conditionNode = invokeZeroArg(caseNode, "condition").orElse(null);
      Optional<Boolean> condition = evaluateNodeAsBoolean(
          conditionNode, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      if (condition.isEmpty() || !condition.get()) {
        continue;
      }
      Object valueNode = unwrapCaseValueNode(invokeZeroArg(caseNode, "value").orElse(null));
      if (valueNode == null) {
        return Optional.empty();
      }
      return GeneratedP4NumberAstEvaluator.tryEvaluate(valueNode, specifiedExpressionTypes, context).map(v -> (Object) v);
    }
    Object defaultValueNode = unwrapCaseValueNode(invokeZeroArg(defaultNode, "value").orElse(null));
    if (defaultValueNode == null) {
      return Optional.empty();
    }
    return GeneratedP4NumberAstEvaluator.tryEvaluate(defaultValueNode, specifiedExpressionTypes, context).map(v -> (Object) v);
  }

  private static Optional<String> evaluateStringMatchExpression(Object matchNode, CalculationContext context,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader, String fallbackFormulaSource) {
    Object firstCaseNode = invokeZeroArg(matchNode, "firstCase").orElse(null);
    Object moreCasesNode = invokeZeroArg(matchNode, "moreCases").orElse(null);
    Object defaultNode = invokeZeroArg(matchNode, "defaultCase").orElse(null);
    if (defaultNode == null) {
      return Optional.empty();
    }
    List<Object> cases = mergeCaseNodes(firstCaseNode, moreCasesNode);
    for (Object caseNode : cases) {
      if (caseNode == null) {
        continue;
      }
      Object conditionNode = invokeZeroArg(caseNode, "condition").orElse(null);
      Optional<Boolean> condition = evaluateNodeAsBoolean(
          conditionNode, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      if (condition.isEmpty() || !condition.get()) {
        continue;
      }
      Object valueNode = unwrapCaseValueNode(invokeZeroArg(caseNode, "value").orElse(null));
      if (valueNode == null) {
        return Optional.empty();
      }
      return evaluateString(valueNode, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
    }
    Object defaultValueNode = unwrapCaseValueNode(invokeZeroArg(defaultNode, "value").orElse(null));
    if (defaultValueNode == null) {
      return Optional.empty();
    }
    return evaluateString(defaultValueNode, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
  }

  private static Optional<Boolean> evaluateBooleanMatchExpression(Object matchNode, CalculationContext context,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader, String fallbackFormulaSource) {
    Object firstCaseNode = invokeZeroArg(matchNode, "firstCase").orElse(null);
    Object moreCasesNode = invokeZeroArg(matchNode, "moreCases").orElse(null);
    Object defaultNode = invokeZeroArg(matchNode, "defaultCase").orElse(null);
    if (defaultNode == null) {
      return Optional.empty();
    }
    List<Object> cases = mergeCaseNodes(firstCaseNode, moreCasesNode);
    for (Object caseNode : cases) {
      if (caseNode == null) {
        continue;
      }
      Object conditionNode = invokeZeroArg(caseNode, "condition").orElse(null);
      Optional<Boolean> condition = evaluateNodeAsBoolean(
          conditionNode, specifiedExpressionTypes, context, classLoader, fallbackFormulaSource);
      if (condition.isEmpty() || !condition.get()) {
        continue;
      }
      Object valueNode = unwrapCaseValueNode(invokeZeroArg(caseNode, "value").orElse(null));
      if (valueNode == null) {
        return Optional.empty();
      }
      return evaluateBoolean(valueNode, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
    }
    Object defaultValueNode = unwrapCaseValueNode(invokeZeroArg(defaultNode, "value").orElse(null));
    if (defaultValueNode == null) {
      return Optional.empty();
    }
    return evaluateBoolean(defaultValueNode, context, specifiedExpressionTypes, classLoader, fallbackFormulaSource);
  }

  private static List<Object> mergeCaseNodes(Object firstCaseNode, Object moreCasesNode) {
    List<Object> merged = new ArrayList<>();
    if (firstCaseNode != null) {
      merged.add(firstCaseNode);
    }
    if (moreCasesNode instanceof List<?> moreCases) {
      for (Object moreCase : moreCases) {
        if (moreCase != null) {
          merged.add(moreCase);
        }
      }
    }
    return merged;
  }

  private static Object unwrapCaseValueNode(Object node) {
    Object current = node;
    while (current != null) {
      String simpleName = current.getClass().getSimpleName();
      if (!"ExpressionExpr".equals(simpleName) && !simpleName.endsWith("CaseValueExpr")) {
        break;
      }
      Object value = invokeZeroArg(current, "value").orElse(null);
      if (value == null || value == current) {
        return null;
      }
      current = value;
    }
    return current;
  }

  private static Optional<Object> evaluateMethodInvocation(Object methodInvocationNode, ExpressionType expectedType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String sourceFormula) {
    Object name = invokeZeroArg(methodInvocationNode, "name").orElse(null);
    if (name == null) {
      return Optional.empty();
    }
    String methodName = String.valueOf(name).strip();
    if (methodName.isEmpty()) {
      return Optional.empty();
    }
    MethodSource method = findMethodSource(sourceFormula, methodName);
    if (method == null) {
      return Optional.empty();
    }
    if (method.expression().isBlank()) {
      return Optional.empty();
    }
    if (isDirectSelfCall(method.expression(), method.name())) {
      return Optional.empty();
    }
    ClassLoader effectiveClassLoader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    List<MethodParameterSpec> parameterSpecs = parseMethodParameterSpecs(method.parameterSection());
    List<String> argumentExpressions =
        resolveInvocationArgumentExpressions(methodInvocationNode, sourceFormula, methodName);
    if (parameterSpecs.size() != argumentExpressions.size()) {
      return Optional.empty();
    }
    Map<String, Object> localBindings =
        bindMethodArguments(parameterSpecs, argumentExpressions, specifiedExpressionTypes, context,
            effectiveClassLoader, sourceFormula);
    if (localBindings == null) {
      return Optional.empty();
    }
    CalculationContext scopedContext =
        localBindings.isEmpty() ? context : new ScopedCalculationContext(context, localBindings);
    SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
        expectedType, resolveNumberTypeForEvaluation(expectedType, specifiedExpressionTypes.numberType()));
    for (String preferredAstSimpleName : preferredAstSimpleNames(expectedType, method.expression())) {
      Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
          method.expression(), effectiveClassLoader, preferredAstSimpleName);
      if (mapped.isEmpty()) {
        continue;
      }
      Optional<Object> evaluated = tryEvaluate(
          mapped.get(), evalTypes, scopedContext, effectiveClassLoader, sourceFormula);
      if (evaluated.isPresent()) {
        return evaluated;
      }
    }
    return evaluateEmbedded(
        method.expression(), expectedType, evalTypes, scopedContext, effectiveClassLoader, sourceFormula);
  }

  private static Map<String, Object> bindMethodArguments(List<MethodParameterSpec> parameterSpecs,
      List<String> argumentExpressions, SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context,
      ClassLoader classLoader, String sourceFormula) {
    if (parameterSpecs.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> bindings = new LinkedHashMap<>();
    for (int i = 0; i < parameterSpecs.size(); i++) {
      MethodParameterSpec parameter = parameterSpecs.get(i);
      Optional<Object> argumentValue = evaluateArgumentExpression(
          argumentExpressions.get(i), parameter.type(), specifiedExpressionTypes, context, classLoader, sourceFormula);
      if (argumentValue.isEmpty()) {
        return null;
      }
      bindings.put(parameter.name(), argumentValue.get());
    }
    return bindings;
  }

  private static Optional<Object> evaluateArgumentExpression(String argumentExpression, ExpressionType parameterType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String sourceFormula) {
    String source = argumentExpression == null ? "" : argumentExpression.strip();
    if (source.isEmpty()) {
      return Optional.empty();
    }
    if (source.startsWith("$")) {
      Optional<Object> resolved = resolveVariableAny(extractVariableName(source), context);
      if (resolved.isPresent()) {
        return coerceToParameterType(resolved.get(), parameterType, specifiedExpressionTypes.numberType());
      }
    }
    SpecifiedExpressionTypes argumentTypes = new SpecifiedExpressionTypes(
        parameterType, resolveNumberTypeForEvaluation(parameterType, specifiedExpressionTypes.numberType()));
    for (String preferredAstSimpleName : preferredAstSimpleNames(parameterType, source)) {
      Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(source, classLoader, preferredAstSimpleName);
      if (mapped.isEmpty()) {
        continue;
      }
      Optional<Object> evaluated = tryEvaluate(
          mapped.get(), argumentTypes, context, classLoader, sourceFormula);
      if (evaluated.isPresent()) {
        return coerceToParameterType(evaluated.get(), parameterType, specifiedExpressionTypes.numberType());
      }
    }
    Optional<Object> embedded = evaluateEmbedded(
        source, parameterType, argumentTypes, context, classLoader, sourceFormula);
    if (embedded.isPresent()) {
      return coerceToParameterType(embedded.get(), parameterType, specifiedExpressionTypes.numberType());
    }
    Optional<Object> literal = parseLiteralArgument(source, parameterType, specifiedExpressionTypes.numberType());
    if (literal.isPresent()) {
      return literal;
    }
    if (parameterType != null && parameterType.isObject()) {
      return Optional.of(source);
    }
    return Optional.empty();
  }

  private static Optional<Object> parseLiteralArgument(String source, ExpressionType parameterType,
      ExpressionType defaultNumberType) {
    if (source.isEmpty()) {
      return Optional.empty();
    }
    if (source.length() >= 2 && source.charAt(0) == '\'' && source.charAt(source.length() - 1) == '\'') {
      return Optional.of(source.substring(1, source.length() - 1));
    }
    Optional<Boolean> bool = toBoolean(source);
    if (bool.isPresent()) {
      return bool.map(v -> (Object) v);
    }
    if (isNumericLiteral(source)) {
      ExpressionType numberType = resolveNumberTypeForEvaluation(parameterType, defaultNumberType);
      try {
        return Optional.of(numberType.parseNumber(source));
      } catch (Throwable ignored) {
      }
    }
    return Optional.empty();
  }

  private static Optional<Object> coerceToParameterType(Object value, ExpressionType parameterType,
      ExpressionType defaultNumberType) {
    if (value == null || parameterType == null) {
      return Optional.ofNullable(value);
    }
    if (parameterType.isObject()) {
      return Optional.of(value);
    }
    if (parameterType.isString()) {
      return Optional.of(String.valueOf(value));
    }
    if (parameterType.isBoolean()) {
      return toBoolean(value).map(v -> (Object) v);
    }
    if (parameterType.isNumber()) {
      if (value instanceof Number number) {
        return Optional.of(castNumber(number, resolveNumberTypeForEvaluation(parameterType, defaultNumberType)));
      }
      if (value instanceof String text && isNumericLiteral(text.strip())) {
        try {
          return Optional.of(resolveNumberTypeForEvaluation(parameterType, defaultNumberType).parseNumber(text.strip()));
        } catch (Throwable ignored) {
        }
      }
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private static Number castNumber(Number number, ExpressionType targetType) {
    if (targetType == null) {
      return number;
    }
    if (targetType.isByte()) {
      return number.byteValue();
    }
    if (targetType.isShort()) {
      return number.shortValue();
    }
    if (targetType.isInt()) {
      return number.intValue();
    }
    if (targetType.isLong()) {
      return number.longValue();
    }
    if (targetType.isDouble()) {
      return number.doubleValue();
    }
    if (targetType.isBigInteger()) {
      return new java.math.BigInteger(String.valueOf(number.longValue()));
    }
    if (targetType.isBigDecimal()) {
      return new java.math.BigDecimal(String.valueOf(number));
    }
    return number.floatValue();
  }

  private static ExpressionType resolveNumberTypeForEvaluation(ExpressionType resultType, ExpressionType numberType) {
    if (resultType != null && resultType.isNumber() && resultType != ExpressionTypes.number) {
      return resultType;
    }
    if (numberType != null && numberType != ExpressionTypes.number) {
      return numberType;
    }
    return ExpressionTypes._float;
  }

  private static List<String> resolveInvocationArgumentExpressions(Object methodInvocationNode, String sourceFormula,
      String methodName) {
    Optional<String> invocationSnippet = sourceSnippetOfNode(methodInvocationNode, sourceFormula);
    if (invocationSnippet.isPresent()) {
      List<String> fromSnippet = parseMethodInvocationArguments(invocationSnippet.get());
      if (fromSnippet != null) {
        return fromSnippet;
      }
    }
    return findInvocationArguments(sourceFormula, methodName);
  }

  private static Optional<String> sourceSnippetOfNode(Object node, String sourceFormula) {
    if (node == null || sourceFormula == null || sourceFormula.isEmpty()) {
      return Optional.empty();
    }
    try {
      String mapperClassName = node.getClass().getPackageName() + ".TinyExpressionP4Mapper";
      Class<?> mapperClass = Class.forName(mapperClassName, false, node.getClass().getClassLoader());
      Method sourceSpanOf = mapperClass.getMethod("sourceSpanOf", Object.class);
      Object spanObj = sourceSpanOf.invoke(null, node);
      if (!(spanObj instanceof Optional<?> spanOptional) || spanOptional.isEmpty()) {
        return Optional.empty();
      }
      Object span = spanOptional.get();
      if (!(span instanceof int[] positions) || positions.length < 2) {
        return Optional.empty();
      }
      int start = Math.max(0, Math.min(sourceFormula.length(), positions[0]));
      int end = Math.max(0, Math.min(sourceFormula.length(), positions[1]));
      if (end <= start) {
        return Optional.empty();
      }
      return Optional.of(sourceFormula.substring(start, end));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private static List<String> parseMethodInvocationArguments(String invocationSource) {
    if (invocationSource == null) {
      return null;
    }
    int openParen = invocationSource.indexOf('(');
    if (openParen < 0) {
      return null;
    }
    int closeParen = findMatching(invocationSource, openParen, '(', ')');
    if (closeParen < 0) {
      return null;
    }
    String arguments = invocationSource.substring(openParen + 1, closeParen).strip();
    return splitTopLevelCommaSeparated(arguments);
  }

  private static List<String> findInvocationArguments(String sourceFormula, String methodName) {
    if (sourceFormula == null || methodName == null || methodName.isBlank()) {
      return List.of();
    }
    String source = sourceFormula;
    String callKeyword = TinyExpressionKeywords.CALL;
    int from = 0;
    while (from < source.length()) {
      int callIndex = source.indexOf(callKeyword, from);
      if (callIndex < 0) {
        return List.of();
      }
      if (!TinyExpressionParserCapabilities.matchesWordAt(source, callIndex, callKeyword)) {
        from = callIndex + callKeyword.length();
        continue;
      }
      int nameStart = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, callIndex + callKeyword.length());
      if (!TinyExpressionParserCapabilities.matchesWordAt(source, nameStart, methodName)) {
        from = callIndex + callKeyword.length();
        continue;
      }
      int openParen = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, nameStart + methodName.length());
      if (openParen >= source.length() || source.charAt(openParen) != '(') {
        from = callIndex + callKeyword.length();
        continue;
      }
      int closeParen = findMatching(source, openParen, '(', ')');
      if (closeParen < 0) {
        return List.of();
      }
      String arguments = source.substring(openParen + 1, closeParen).strip();
      return splitTopLevelCommaSeparated(arguments);
    }
    return List.of();
  }

  private static boolean isDirectSelfCall(String expression, String methodName) {
    if (expression == null || methodName == null || methodName.isBlank()) {
      return false;
    }
    String callKeyword = TinyExpressionKeywords.CALL;
    int index = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(expression, 0);
    if (!TinyExpressionParserCapabilities.matchesWordAt(expression, index, callKeyword)) {
      return false;
    }
    int nameStart = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(expression, index + callKeyword.length());
    if (!TinyExpressionParserCapabilities.matchesWordAt(expression, nameStart, methodName)) {
      return false;
    }
    int openParen = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(expression, nameStart + methodName.length());
    return openParen < expression.length() && expression.charAt(openParen) == '(';
  }

  private static List<MethodParameterSpec> parseMethodParameterSpecs(String parameterSection) {
    if (parameterSection == null || parameterSection.isBlank()) {
      return List.of();
    }
    List<String> entries = splitTopLevelCommaSeparated(parameterSection.strip());
    List<MethodParameterSpec> parameters = new ArrayList<>();
    for (String entry : entries) {
      Optional<MethodParameterSpec> parsed = parseMethodParameterSpec(entry);
      if (parsed.isEmpty()) {
        return List.of();
      }
      parameters.add(parsed.get());
    }
    return parameters;
  }

  private static Optional<MethodParameterSpec> parseMethodParameterSpec(String parameterSpec) {
    if (parameterSpec == null) {
      return Optional.empty();
    }
    String spec = parameterSpec.strip();
    if (spec.isEmpty() || spec.charAt(0) != '$') {
      return Optional.empty();
    }
    int end = 1;
    while (end < spec.length()) {
      char c = spec.charAt(end);
      if (Character.isLetterOrDigit(c) || c == '_') {
        end++;
        continue;
      }
      break;
    }
    if (end <= 1) {
      return Optional.empty();
    }
    String name = spec.substring(1, end);
    ExpressionType type = ExpressionTypes.object;
    String tail = spec.substring(end).strip();
    if (!tail.isEmpty()) {
      String[] tokens = tail.split("\\s+");
      for (int i = 0; i < tokens.length - 1; i++) {
        if ("as".equalsIgnoreCase(tokens[i])) {
          type = parseExpressionType(tokens[i + 1]).orElse(ExpressionTypes.object);
          break;
        }
      }
    }
    return Optional.of(new MethodParameterSpec(name, type));
  }

  private static Optional<ExpressionType> parseExpressionType(String token) {
    String type = token == null ? "" : token.strip().toLowerCase();
    return switch (type) {
      case "number" -> Optional.of(ExpressionTypes.number);
      case "float" -> Optional.of(ExpressionTypes._float);
      case "string" -> Optional.of(ExpressionTypes.string);
      case "boolean" -> Optional.of(ExpressionTypes._boolean);
      case "object" -> Optional.of(ExpressionTypes.object);
      default -> Optional.empty();
    };
  }

  private static List<String> splitTopLevelCommaSeparated(String source) {
    String text = source == null ? "" : source.strip();
    if (text.isEmpty()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
      if (inLineComment) {
        current.append(c);
        if (c == '\n') {
          inLineComment = false;
        }
        continue;
      }
      if (inBlockComment) {
        current.append(c);
        if (c == '*' && next == '/') {
          current.append(next);
          i++;
          inBlockComment = false;
        }
        continue;
      }
      if (inSingleQuote) {
        current.append(c);
        if (c == '\'' && (i == 0 || text.charAt(i - 1) != '\\')) {
          inSingleQuote = false;
        }
        continue;
      }
      if (inDoubleQuote) {
        current.append(c);
        if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
          inDoubleQuote = false;
        }
        continue;
      }
      if (c == '/' && next == '/') {
        current.append(c).append(next);
        i++;
        inLineComment = true;
        continue;
      }
      if (c == '/' && next == '*') {
        current.append(c).append(next);
        i++;
        inBlockComment = true;
        continue;
      }
      if (c == '\'') {
        inSingleQuote = true;
        current.append(c);
        continue;
      }
      if (c == '"') {
        inDoubleQuote = true;
        current.append(c);
        continue;
      }
      if (c == '(') {
        parenDepth++;
        current.append(c);
        continue;
      }
      if (c == ')') {
        if (parenDepth > 0) {
          parenDepth--;
        }
        current.append(c);
        continue;
      }
      if (c == '{') {
        braceDepth++;
        current.append(c);
        continue;
      }
      if (c == '}') {
        if (braceDepth > 0) {
          braceDepth--;
        }
        current.append(c);
        continue;
      }
      if (c == '[') {
        bracketDepth++;
        current.append(c);
        continue;
      }
      if (c == ']') {
        if (bracketDepth > 0) {
          bracketDepth--;
        }
        current.append(c);
        continue;
      }
      if (c == ',' && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
        values.add(current.toString().strip());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    values.add(current.toString().strip());
    return values;
  }

  private static MethodSource findMethodSource(String sourceFormula, String methodName) {
    if (sourceFormula == null || methodName == null || methodName.isBlank()) {
      return null;
    }
    String source = sourceFormula;
    int length = source.length();
    int from = 0;
    while (from < length) {
      int nameIdx = source.indexOf(methodName, from);
      if (nameIdx < 0) {
        return null;
      }
      if (!TinyExpressionParserCapabilities.matchesWordAt(source, nameIdx, methodName)) {
        from = nameIdx + methodName.length();
        continue;
      }
      int openParen = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, nameIdx + methodName.length());
      if (openParen >= source.length() || source.charAt(openParen) != '(') {
        from = nameIdx + methodName.length();
        continue;
      }
      int typeEnd = nameIdx;
      int typeStart = skipTypeStart(source, typeEnd);
      if (typeStart < 0) {
        from = nameIdx + methodName.length();
        continue;
      }
      String typeToken = source.substring(typeStart, typeEnd).strip();
      if (!isMethodReturnType(typeToken)) {
        from = nameIdx + methodName.length();
        continue;
      }
      int paramsStart = openParen + 1;
      int paramsEnd = findMatching(source, openParen, '(', ')');
      if (paramsEnd < 0) {
        return null;
      }
      int openBrace = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, paramsEnd + 1);
      if (openBrace < 0 || openBrace >= source.length() || source.charAt(openBrace) != '{') {
        from = nameIdx + methodName.length();
        continue;
      }
      int closeBrace = findMatching(source, openBrace, '{', '}');
      if (closeBrace < 0) {
        return null;
      }
      String params = source.substring(paramsStart, paramsEnd).strip();
      String body = source.substring(openBrace + 1, closeBrace).strip();
      return new MethodSource(methodName, params, body);
    }
    return null;
  }

  private static int skipTypeStart(String source, int typeEndExclusive) {
    int i = typeEndExclusive - 1;
    while (i >= 0 && Character.isWhitespace(source.charAt(i))) {
      i--;
    }
    while (i >= 0 && Character.isJavaIdentifierPart(source.charAt(i))) {
      i--;
    }
    int start = i + 1;
    return start < typeEndExclusive ? start : -1;
  }

  private static boolean isMethodReturnType(String token) {
    String t = token == null ? "" : token.strip();
    return "number".equals(t)
        || "float".equals(t)
        || "string".equals(t)
        || "boolean".equals(t)
        || "object".equals(t);
  }

  private static int findMatching(String source, int openIndex, char open, char close) {
    if (openIndex < 0 || openIndex >= source.length() || source.charAt(openIndex) != open) {
      return -1;
    }
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    for (int i = openIndex; i < source.length(); i++) {
      char c = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
      if (inLineComment) {
        if (c == '\n') {
          inLineComment = false;
        }
        continue;
      }
      if (inBlockComment) {
        if (c == '*' && next == '/') {
          i++;
          inBlockComment = false;
        }
        continue;
      }
      if (inSingleQuote) {
        if (c == '\'' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inSingleQuote = false;
        }
        continue;
      }
      if (inDoubleQuote) {
        if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inDoubleQuote = false;
        }
        continue;
      }
      if (c == '/' && next == '/') {
        inLineComment = true;
        i++;
        continue;
      }
      if (c == '/' && next == '*') {
        inBlockComment = true;
        i++;
        continue;
      }
      if (c == '\'') {
        inSingleQuote = true;
        continue;
      }
      if (c == '"') {
        inDoubleQuote = true;
        continue;
      }
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<String> preferredAstSimpleNames(ExpressionType type, String sourceText) {
    List<String> preferred = new ArrayList<>();
    String source = sourceText == null ? "" : sourceText.strip();
    boolean methodInvocationHead = AstEmbeddedExpressionRuntime.hasMethodInvocationHead(source);
    boolean ifHead = AstEmbeddedExpressionRuntime.hasIfHead(source);
    boolean matchHead = AstEmbeddedExpressionRuntime.hasMatchHead(source);
    if (methodInvocationHead) {
      preferred.add("MethodInvocationExpr");
    }
    if (ifHead) {
      preferred.add("IfExpr");
    }
    if (type == null) {
      preferred.add(null);
      return preferred;
    }
    if (type.isNumber()) {
      if (matchHead) {
        preferred.add("NumberMatchExpr");
      }
      preferred.add("BinaryExpr");
    } else if (type.isString()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
      }
      preferred.add("StringExpr");
    } else if (type.isBoolean()) {
      if (matchHead) {
        preferred.add("BooleanMatchExpr");
      }
      preferred.add("BooleanExpr");
    } else if (type.isObject()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
        preferred.add("BooleanMatchExpr");
        preferred.add("NumberMatchExpr");
      }
      preferred.add("ObjectExpr");
      preferred.add("StringExpr");
      preferred.add("BooleanExpr");
      preferred.add("BinaryExpr");
    } else {
      preferred.add(null);
    }
    preferred.add("MethodInvocationExpr");
    preferred.add("VariableRefExpr");
    preferred.add("IfExpr");
    preferred.add("BinaryExpr");
    return preferred.stream().distinct().collect(Collectors.toList());
  }

  private static boolean isNumericLiteral(String source) {
    if (source == null || source.isBlank()) {
      return false;
    }
    int i = 0;
    String text = source.strip();
    if (text.charAt(0) == '+' || text.charAt(0) == '-') {
      i++;
      if (i >= text.length()) {
        return false;
      }
    }
    boolean hasDigit = false;
    boolean hasDot = false;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isDigit(c)) {
        hasDigit = true;
        continue;
      }
      if (c == '.' && !hasDot) {
        hasDot = true;
        continue;
      }
      return false;
    }
    return hasDigit;
  }

  private record MethodSource(String name, String parameterSection, String expression) {}

  private record MethodParameterSpec(String name, ExpressionType type) {}

  private static final class ScopedCalculationContext implements CalculationContext {

    private final CalculationContext delegate;
    private final Map<String, Object> localValues;

    private ScopedCalculationContext(CalculationContext delegate, Map<String, Object> initialValues) {
      this.delegate = delegate;
      this.localValues = new HashMap<>(initialValues);
    }

    @Override
    public void set(String name, String value) {
      localValues.put(name, value);
    }

    @Override
    public Optional<String> getString(String name) {
      Object local = localValues.get(name);
      if (local instanceof String text) {
        return Optional.of(text);
      }
      return delegate.getString(name);
    }

    @Override
    public void set(String name, float value) {
      localValues.put(name, value);
    }

    @Override
    public Optional<Float> getValue(String name) {
      Object local = localValues.get(name);
      if (local instanceof Number number) {
        return Optional.of(number.floatValue());
      }
      return delegate.getValue(name);
    }

    @Override
    public void set(String name, Number value) {
      localValues.put(name, value);
    }

    @Override
    public Optional<? extends Number> getNumber(String name) {
      Object local = localValues.get(name);
      if (local instanceof Number number) {
        return Optional.of(number);
      }
      return delegate.getNumber(name);
    }

    @Override
    public void set(String name, boolean value) {
      localValues.put(name, value);
    }

    @Override
    public Optional<Boolean> getBoolean(String name) {
      Object local = localValues.get(name);
      if (local instanceof Boolean bool) {
        return Optional.of(bool);
      }
      return delegate.getBoolean(name);
    }

    @Override
    public <T> Optional<T> getObject(String name, Class<T> clazz) {
      Object local = localValues.get(name);
      if (local != null && clazz.isInstance(local)) {
        return Optional.of(clazz.cast(local));
      }
      return delegate.getObject(name, clazz);
    }

    @Override
    public void setObject(String name, Object object) {
      localValues.put(name, object);
    }

    @Override
    public boolean isExists(String name) {
      if (localValues.containsKey(name) && localValues.get(name) != null) {
        return true;
      }
      return delegate.isExists(name);
    }

    @Override
    public double radianAngle(double angleValue) {
      return delegate.radianAngle(angleValue);
    }

    @Override
    public float nextRandom() {
      return delegate.nextRandom();
    }

    @Override
    public Angle angle() {
      return delegate.angle();
    }

    @Override
    public int scale() {
      return delegate.scale();
    }

    @Override
    public java.math.RoundingMode roundingMode() {
      return delegate.roundingMode();
    }

    @Override
    public boolean inDayTimeRange(java.time.DayOfWeek fromDayInclusive, float fromDayHourInclusive,
        java.time.DayOfWeek toDayInclusive, float toDayHourExclusive) {
      return delegate.inDayTimeRange(fromDayInclusive, fromDayHourInclusive, toDayInclusive, toDayHourExclusive);
    }
  }

  private static String normalizeComparisonOperator(Object opNode) {
    if (opNode == null) {
      return null;
    }
    if (opNode instanceof List<?> list) {
      if (list.isEmpty()) {
        return null;
      }
      return String.valueOf(list.get(0)).strip();
    }
    return String.valueOf(opNode).strip();
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bigDecimal) {
      return bigDecimal;
    }
    return new BigDecimal(String.valueOf(value));
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
