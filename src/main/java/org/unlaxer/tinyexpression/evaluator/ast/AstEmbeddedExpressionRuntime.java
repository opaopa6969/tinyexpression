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
import org.unlaxer.tinyexpression.parser.TinyExpressionKeywords;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

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

  static Optional<Object> tryEvaluateFormulaDirect(String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext context, ClassLoader classLoader) {
    String expression = expressionSource == null ? "" : expressionSource.strip();
    if (expression.isEmpty()) {
      return Optional.empty();
    }
    return evaluateFormula(expression, resultType, specifiedExpressionTypes, context, classLoader);
  }

  static boolean isLikelyExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return isLikelyStructuredExpression(normalized)
        || isLikelyBooleanComparisonExpression(normalized)
        || normalized.contains("->")
        || hasDeclarationOrImportKeyword(normalized)
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
        || looksLikeStringStructuredExpression(normalized)
        || normalized.contains("->");
  }

  static boolean hasIfHead(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return TinyExpressionParserCapabilities.hasHead(normalized, TinyExpressionKeywords.IF, '(');
  }

  static boolean hasMatchHead(String text) {
    String normalized = text == null ? "" : text.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    return TinyExpressionParserCapabilities.hasHead(normalized, TinyExpressionKeywords.MATCH, '{');
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

  private static boolean hasDeclarationOrImportKeyword(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    // Check if the text starts with "import" or "var"/"variable" keyword
    if (TinyExpressionParserCapabilities.hasHead(text, "import", null)) {
      return true;
    }
    for (String keyword : TinyExpressionKeywords.VARIABLE_DECLARATION_HEADS) {
      if (TinyExpressionParserCapabilities.hasHead(text, keyword, null)) {
        return true;
      }
    }
    // Check if the text contains "external"/"call"/"internal" anywhere (multi-line formulas)
    for (String keyword : TinyExpressionKeywords.METHOD_INVOCATION_HEADS) {
      if (text.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isMethodInvocationExpression(String text) {
    String normalized = text == null ? "" : text.strip();
    for (String keyword : TinyExpressionKeywords.METHOD_INVOCATION_HEADS) {
      if (TinyExpressionParserCapabilities.hasHead(normalized, keyword, null)) {
        return true;
      }
    }
    return false;
  }

  private static boolean looksLikeStringStructuredExpression(String normalized) {
    String unwrapped = unwrapWholeParentheses(normalized);
    if (!unwrapped.equals(normalized)) {
      return looksLikeStringStructuredExpression(unwrapped)
          || hasTopLevelStringConcat(unwrapped);
    }
    return normalized.startsWith("trim(")
        || normalized.startsWith("toUpperCase(")
        || normalized.startsWith("toLowerCase(")
        || normalized.startsWith("startsWith(")
        || normalized.startsWith("endsWith(")
        || normalized.startsWith("contains(")
        || normalized.startsWith("slice(")
        || normalized.contains(".trim(")
        || normalized.contains(".toUpperCase(")
        || normalized.contains(".toLowerCase(")
        || normalized.contains(".startsWith(")
        || normalized.contains(".endsWith(")
        || normalized.contains(".contains(")
        || (normalized.indexOf('[') >= 0 && normalized.endsWith("]"));
  }

  private static String unwrapWholeParentheses(String text) {
    String current = text == null ? "" : text.strip();
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
