package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.Optional;
import java.util.regex.Pattern;

import org.unlaxer.Name;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

final class AstEmbeddedExpressionRuntime {

  private static final Pattern BOOLEAN_COMPARISON_PATTERN = Pattern.compile(
      "(?s).*(?:\\$[A-Za-z_][A-Za-z0-9_]*|[-+]?\\d+(?:\\.\\d+)?)\\s*(==|!=|<=|>=|<|>)\\s*"
          + "(?:\\$[A-Za-z_][A-Za-z0-9_]*|[-+]?\\d+(?:\\.\\d+)?).*");

  private AstEmbeddedExpressionRuntime() {}

  static Optional<Object> tryEvaluate(String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader,
      String fallbackFormulaSource) {
    String expression = expressionSource == null ? "" : expressionSource.strip();
    if (expression.isEmpty() || !isLikelyExpression(expression)) {
      return Optional.empty();
    }
    Optional<Object> direct = evaluateFormula(expression, resultType, specifiedExpressionTypes, context, classLoader);
    if (direct.isPresent()) {
      return direct;
    }
    String fallback = fallbackFormulaSource == null ? "" : fallbackFormulaSource.strip();
    if (fallback.isEmpty() || fallback.equals(expression) || !isMethodInvocationExpression(expression)) {
      return Optional.empty();
    }
    return evaluateFormula(fallback, resultType, specifiedExpressionTypes, context, classLoader);
  }

  static boolean isLikelyExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return isLikelyStructuredExpression(normalized)
        || isLikelyBooleanComparisonExpression(normalized)
        || normalized.contains("->")
        ;
  }

  static boolean isLikelyStructuredExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return hasIfHead(normalized)
        || hasMatchHead(normalized)
        || hasMethodInvocationHead(normalized)
        || normalized.contains("->");
  }

  static boolean hasIfHead(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return JavaStyleSourceProbe.hasHead(normalized, "if", '(');
  }

  static boolean hasMatchHead(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return JavaStyleSourceProbe.hasHead(normalized, "match", '{');
  }

  static boolean hasMethodInvocationHead(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return isMethodInvocationExpression(normalized);
  }

  static boolean isLikelyBooleanComparisonExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return BOOLEAN_COMPARISON_PATTERN.matcher(normalized).matches();
  }

  private static boolean isMethodInvocationExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    return JavaStyleSourceProbe.hasHead(normalized, "call", null)
        || JavaStyleSourceProbe.hasHead(normalized, "external", null)
        || JavaStyleSourceProbe.hasHead(normalized, "internal", null);
  }

  private static Optional<Object> evaluateFormula(String formula, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader) {
    try {
      ClassLoader effectiveClassLoader =
          classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
      ExpressionType effectiveResultType = resultType == null ? ExpressionTypes.object : resultType;
      SpecifiedExpressionTypes evalTypes = new SpecifiedExpressionTypes(
          effectiveResultType, resolveNumberType(specifiedExpressionTypes, effectiveResultType));
      JavaCodeCalculatorV3 calculator = new JavaCodeCalculatorV3(
          Name.of("AstEmbeddedExpression"),
          new Source(formula),
          evalTypes,
          effectiveClassLoader);
      return Optional.ofNullable(calculator.apply(context));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private static ExpressionType resolveNumberType(SpecifiedExpressionTypes specifiedExpressionTypes,
      ExpressionType resultType) {
    if (resultType != null && resultType.isNumber() && resultType != ExpressionTypes.number) {
      return resultType;
    }
    if (specifiedExpressionTypes.numberType() != null) {
      return specifiedExpressionTypes.numberType();
    }
    return ExpressionTypes._float;
  }
}
