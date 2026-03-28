package org.unlaxer.tinyexpression.evaluator.ast;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.SetterParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser.VariableInfo;

final class AstDeclarationRuntime {

  private AstDeclarationRuntime() {}

  record MainExpressionEvaluation(Object value, String runtime) {}

  static boolean applyDeclarations(String source, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext, ClassLoader classLoader) {
    try {
      Token rootToken = parseAndReduce(source);
      TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken, specifiedExpressionTypes);
      String methodDeclarationsSource = tinyExpressionTokens.getMethodTokens().stream()
          .map(AstDeclarationRuntime::tokenTextCompat)
          .filter(text -> text != null && !text.isBlank())
          .collect(Collectors.joining("\n"));
      boolean changed = false;
      for (Token declarationToken : tinyExpressionTokens.getVariableDeclarationTokens()) {
        changed |= applyDeclaration(
            declarationToken, specifiedExpressionTypes, calculationContext, classLoader, methodDeclarationsSource, source);
      }
      return changed;
    } catch (Throwable ignored) {
      return false;
    }
  }

  static Optional<MainExpressionEvaluation> tryEvaluateMainExpression(
      String source, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext, ClassLoader classLoader) {
    try {
      if (hasInternalJavaCommentAfterLeadingDelimiters(source)) {
        return Optional.empty();
      }
      Token rootToken = parseAndReduce(source);
      TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(rootToken, specifiedExpressionTypes);
      if (tinyExpressionTokens.getVariableDeclarationTokens().isEmpty()) {
        return Optional.empty();
      }
      String methodDeclarationsSource = tinyExpressionTokens.getMethodTokens().stream()
          .map(AstDeclarationRuntime::tokenTextCompat)
          .filter(text -> text != null && !text.isBlank())
          .collect(Collectors.joining("\n"));

      // Use a scoped context to avoid leaking variable declarations to the caller's context.
      CalculationContext scopedContext = createScopedContext(calculationContext);

      for (Token declarationToken : tinyExpressionTokens.getVariableDeclarationTokens()) {
        applyDeclaration(
            declarationToken, specifiedExpressionTypes, scopedContext, classLoader, methodDeclarationsSource, source);
      }
      String expressionSource = tokenTextCompat(tinyExpressionTokens.getExpressionToken());
      // Fallback: if token text extraction is unreliable, extract expression from raw source
      if (expressionSource == null || expressionSource.isBlank()
          || !isPlausibleExpression(expressionSource, source)) {
        expressionSource = extractExpressionFromSource(source);
      }
      if (expressionSource == null || expressionSource.isBlank()) {
        return Optional.empty();
      }
      ExpressionType resultType = specifiedExpressionTypes.resultType() == null
          ? ExpressionTypes.object
          : specifiedExpressionTypes.resultType();
      return evaluateExpressionWithMode(
          expressionSource, resultType, specifiedExpressionTypes, scopedContext, classLoader,
          methodDeclarationsSource)
          .map(result -> new MainExpressionEvaluation(result.value(), result.runtime()));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  /**
   * Creates a scoped context that contains all entries from the parent context
   * but does not write back to it. This prevents variable declarations from leaking
   * to subsequent formula evaluations.
   */
  private static CalculationContext createScopedContext(CalculationContext parent) {
    CalculationContext scoped = new org.unlaxer.tinyexpression.ConcurrentCalculationContext(
        parent.scale(), parent.roundingMode(), parent.angle());
    // Copy existing variables from parent to scoped context
    if (parent instanceof org.unlaxer.tinyexpression.AbstractCalculationContext abc) {
      abc.valueByName.forEach((k, v) -> {
        if (k != null && v != null) scoped.set(k, v);
      });
      abc.booleanByName.forEach((k, v) -> {
        if (k != null && v != null) scoped.set(k, v);
      });
      abc.stringByName.forEach((k, v) -> {
        if (k != null && v != null) scoped.set(k, v);
      });
      abc.objectByName.forEach((k, v) -> {
        if (k != null && v != null) scoped.setObject(k, v);
      });
    }
    return scoped;
  }

  private static boolean hasInternalJavaCommentAfterLeadingDelimiters(String source) {
    return TinyExpressionParserCapabilities.containsCommentAfterLeadingJavaStyleDelimiters(source);
  }

  private static boolean applyDeclaration(Token declarationToken, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext, ClassLoader classLoader, String methodDeclarationsSource, String fullSource) {
    Optional<Token> setterToken = declarationToken.getChildAsOptional(TokenPredicators.parserImplements(SetterParser.class));
    if (setterToken.isEmpty()) {
      return false;
    }
    VariableInfo variableInfo = VariableDeclarationParser.extractVariableInfo(declarationToken);
    if (variableInfo == null || variableInfo.name == null || variableInfo.name.isEmpty()) {
      return false;
    }
    boolean ifNotExists = setterToken.get().getChildWithParserAsOptional(IfNotExistsParser.class).isPresent();
    if (ifNotExists && calculationContext.isExists(variableInfo.name)) {
      return false;
    }
    Optional<Token> expressionToken =
        setterToken.get().getChildAsOptional(TokenPredicators.parserImplements(ExpressionInterface.class));
    if (expressionToken.isEmpty()) {
      return false;
    }
    String expressionSource = tokenTextCompat(expressionToken.get());
    // Validate: if expressionSource doesn't look right, try extracting from the full source
    if (expressionSource == null || expressionSource.isBlank()
        || !isPlausibleSetterExpression(expressionSource, fullSource)) {
      String extracted = extractSetterExpression(fullSource, variableInfo.name);
      if (extracted != null && !extracted.isBlank()) {
        expressionSource = extracted;
      }
    }
    if (expressionSource == null || expressionSource.isBlank()) {
      return false;
    }
    Optional<Object> evaluated = evaluateExpression(
        expressionSource, variableInfo.expressionType, specifiedExpressionTypes, calculationContext, classLoader,
        methodDeclarationsSource);
    if (evaluated.isEmpty()) {
      return false;
    }
    setVariable(variableInfo.name, variableInfo.expressionType, evaluated.get(),
        specifiedExpressionTypes, calculationContext);
    return true;
  }

  private static Optional<Object> evaluateExpression(String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext calculationContext, ClassLoader classLoader,
      String methodDeclarationsSource) {
    return evaluateExpressionWithMode(
        expressionSource, resultType, specifiedExpressionTypes, calculationContext, classLoader, methodDeclarationsSource)
        .map(EvalResult::value);
  }

  private record EvalResult(Object value, String runtime) {}

  private static Optional<EvalResult> evaluateExpressionWithMode(
      String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext calculationContext, ClassLoader classLoader,
      String methodDeclarationsSource) {
    String embeddedFormulaSource = joinExpressionWithMethods(expressionSource, methodDeclarationsSource);
    SpecifiedExpressionTypes evalTypes =
        new SpecifiedExpressionTypes(resultType, resolveNumberType(specifiedExpressionTypes, resultType));
    if (GeneratedAstRuntimeProbe.isAvailable(classLoader)) {
      for (String preferredAstSimpleName : preferredAstSimpleNames(expressionSource, resultType)) {
        Optional<Object> mapped = GeneratedAstRuntimeProbe.tryMapAst(
            expressionSource, classLoader, preferredAstSimpleName);
        if (mapped.isPresent()) {
          // Try P4TypedAstEvaluator first (sealed-interface dispatch, context-aware)
          if (mapped.get() instanceof TinyExpressionP4AST typedAst) {
            try {
              Object p4Result = new P4TypedAstEvaluator(evalTypes, calculationContext).eval(typedAst);
              if (p4Result != null) {
                return Optional.of(new EvalResult(p4Result, "p4-typed"));
              }
            } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
              // fall through to reflection-based evaluator
            }
          }
          Optional<Object> generatedValue =
              GeneratedP4ValueAstEvaluator.tryEvaluate(
                  mapped.get(), evalTypes, calculationContext, classLoader, embeddedFormulaSource);
          if (generatedValue.isPresent()) {
            return generatedValue.map(value -> new EvalResult(value, "generated-ast"));
          }
        }
      }
    }
    if (resultType.isNumber()) {
      Optional<Object> number = AstNumberExpressionEvaluator.tryEvaluate(expressionSource, evalTypes, calculationContext);
      if (number.isPresent()) {
        return number.map(value -> new EvalResult(value, "token-ast"));
      }
      return AstEmbeddedExpressionRuntime.tryEvaluate(
          expressionSource, resultType, evalTypes, calculationContext, classLoader, embeddedFormulaSource)
          .map(value -> new EvalResult(value, "embedded-bridge"));
    }
    Optional<Object> literalOrVariable = parseLiteralOrVariable(
        expressionSource, resultType, specifiedExpressionTypes, calculationContext);
    if (!AstEmbeddedExpressionRuntime.isLikelyExpression(expressionSource)) {
      return literalOrVariable.map(value -> new EvalResult(value, "token-ast"));
    }
    Optional<Object> embedded = AstEmbeddedExpressionRuntime.tryEvaluate(
        expressionSource, resultType, evalTypes, calculationContext, classLoader, embeddedFormulaSource);
    return embedded.map(value -> new EvalResult(value, "embedded-bridge"));
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

  private static List<String> preferredAstSimpleNames(String expressionSource, ExpressionType resultType) {
    List<String> preferred = new ArrayList<>();
    String source = expressionSource == null ? "" : expressionSource.strip();
    boolean methodInvocationHead = AstEmbeddedExpressionRuntime.hasMethodInvocationHead(source);
    boolean ifHead = AstEmbeddedExpressionRuntime.hasIfHead(source);
    boolean matchHead = AstEmbeddedExpressionRuntime.hasMatchHead(source);
    if (methodInvocationHead) {
      preferred.add("MethodInvocationExpr");
    }
    if (ifHead) {
      preferred.add("IfExpr");
    }
    if (resultType == null) {
      preferred.add(null);
      return preferred;
    }
    if (resultType.isNumber()) {
      if (matchHead) {
        preferred.add("NumberMatchExpr");
      }
      preferred.add("BinaryExpr");
    } else if (resultType.isString()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
      }
      preferred.add("StringConcatExpr");
    } else if (resultType.isBoolean()) {
      if (matchHead) {
        preferred.add("BooleanMatchExpr");
      }
      preferred.add("BooleanExpr");
    } else if (resultType.isObject()) {
      if (matchHead) {
        preferred.add("StringMatchExpr");
        preferred.add("BooleanMatchExpr");
        preferred.add("NumberMatchExpr");
      }
      preferred.add("ObjectExpr");
    } else {
      preferred.add(null);
    }
    preferred.add("MethodInvocationExpr");
    preferred.add("VariableRefExpr");
    preferred.add("BinaryExpr");
    preferred.add(null);
    return preferred.stream().distinct().toList();
  }

  private static Optional<Object> parseLiteralOrVariable(String expressionSource, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes, CalculationContext calculationContext) {
    String text = expressionSource == null ? "" : expressionSource.strip();
    if (text.isEmpty()) {
      return Optional.empty();
    }
    if (text.startsWith("$")) {
      return resolveVariable(extractVariableName(text), calculationContext);
    }
    if (text.length() >= 2
        && ((text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\'')
            || (text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"'))) {
      return Optional.of(text.substring(1, text.length() - 1));
    }
    if (resultType != null && resultType.isBoolean()) {
      if ("true".equalsIgnoreCase(text)) {
        return Optional.of(true);
      }
      if ("false".equalsIgnoreCase(text)) {
        return Optional.of(false);
      }
    }
    if (resultType != null && resultType.isNumber()) {
      ExpressionType numberType = resolveNumberType(specifiedExpressionTypes, resultType);
      try {
        return Optional.of(numberType.parseNumber(text));
      } catch (Throwable ignored) {
      }
    }
    return Optional.of(text);
  }

  private static Optional<Object> resolveVariable(String variableName, CalculationContext calculationContext) {
    if (variableName == null || variableName.isEmpty()) {
      return Optional.empty();
    }
    Optional<? extends Number> number = calculationContext.getNumber(variableName);
    if (number.isPresent()) {
      return Optional.of(number.get());
    }
    Optional<String> string = calculationContext.getString(variableName);
    if (string.isPresent()) {
      return Optional.of(string.get());
    }
    Optional<Boolean> bool = calculationContext.getBoolean(variableName);
    if (bool.isPresent()) {
      return Optional.of(bool.get());
    }
    return calculationContext.getObject(variableName, Object.class);
  }

  private static String extractVariableName(String raw) {
    if (raw == null || raw.isEmpty()) {
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

  private static void setVariable(String name, ExpressionType resultType, Object value,
      SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    if (resultType != null && resultType.isNumber()) {
      ExpressionType numberType = resolveNumberType(specifiedExpressionTypes, resultType);
      Number number = value instanceof Number n ? n : numberType.parseNumber(String.valueOf(value));
      calculationContext.set(name, number);
      return;
    }
    if (resultType != null && resultType.isBoolean()) {
      if (value instanceof Boolean bool) {
        calculationContext.set(name, bool);
        return;
      }
      String text = String.valueOf(value).strip().toLowerCase();
      calculationContext.set(name, "true".equals(text));
      return;
    }
    if (resultType != null && resultType.isString()) {
      calculationContext.set(name, value == null ? "" : String.valueOf(value));
      return;
    }
    calculationContext.setObject(name, value);
  }

  private static Token parseAndReduce(String source) {
    Parser parser = Parser.get(TinyExpressionParser.class);
    ParseContext parseContext = new ParseContext(new StringSource(source));
    try (parseContext) {
      Parsed parsed = parser.parse(parseContext);
      if (!parsed.isSucceeded()) {
        throw new IllegalArgumentException("Parse failed for declaration runtime path");
      }
      Token rootToken = parsed.getRootToken(true);
      rootToken = VariableTypeResolver.resolveVariableType(rootToken);
      return OperatorOperandTreeCreator.SINGLETON.apply(rootToken);
    }
  }

  private static String joinExpressionWithMethods(String expressionSource, String methodDeclarationsSource) {
    String expression = expressionSource == null ? "" : expressionSource.strip();
    String methods = methodDeclarationsSource == null ? "" : methodDeclarationsSource.strip();
    if (methods.isEmpty()) {
      return expression;
    }
    if (expression.isEmpty()) {
      return methods;
    }
    return expression + "\n" + methods;
  }

  private static String tokenTextCompat(Token token) {
    if (token == null) {
      return null;
    }
    try {
      return token.getToken().orElse(null);
    } catch (Throwable ignored) {
    }
    try {
      Object source = token.getClass().getField("source").get(token);
      if (source != null) {
        Object value = source.getClass().getMethod("sourceAsString").invoke(source);
        return value == null ? null : String.valueOf(value);
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  /**
   * Check if the setter expression text is plausible given the declaration text.
   */
  private static boolean isPlausibleSetterExpression(String expressionText, String declarationText) {
    if (expressionText == null || expressionText.isBlank()) {
      return false;
    }
    if (declarationText == null) {
      return true;
    }
    // If the expression text appears in the declaration, it's plausible
    return declarationText.contains(expressionText.strip());
  }

  /**
   * Extract the setter default value from the full source, searching for a declaration
   * of the given variable name.
   * Handles: var $x as type set [if not exists] EXPRESSION description='...';
   */
  private static String extractSetterExpression(String fullSource, String variableName) {
    if (fullSource == null || variableName == null) return null;
    // Find the declaration for this variable
    String varRef = "$" + variableName;
    int varIdx = fullSource.indexOf(varRef);
    if (varIdx < 0) return null;
    // Find "set" after the variable
    int setIdx = fullSource.indexOf(" set ", varIdx);
    if (setIdx < 0) return null;
    int exprStart = setIdx + " set ".length();
    // Skip "if not exists" if present
    String afterSet = fullSource.substring(exprStart).stripLeading();
    if (afterSet.startsWith("if not exists ")) {
      exprStart = fullSource.indexOf("if not exists ", exprStart) + "if not exists ".length();
      afterSet = fullSource.substring(exprStart).stripLeading();
      exprStart = fullSource.length() - afterSet.length();
    }
    // Find "description=" or ";" boundary
    int descIdx = fullSource.indexOf(" description=", exprStart);
    int semiIdx = fullSource.indexOf(';', exprStart);
    int boundary = -1;
    if (descIdx >= 0 && semiIdx >= 0) {
      boundary = Math.min(descIdx, semiIdx);
    } else if (descIdx >= 0) {
      boundary = descIdx;
    } else if (semiIdx >= 0) {
      boundary = semiIdx;
    }
    if (boundary < 0) {
      return fullSource.substring(exprStart).strip();
    }
    return fullSource.substring(exprStart, boundary).strip();
  }

  /**
   * Check if the extracted expression text looks plausible.
   * Guard against mangled token text (e.g. "+$price2" instead of "$price+2").
   */
  private static boolean isPlausibleExpression(String expressionText, String fullSource) {
    if (expressionText == null || expressionText.isBlank()) {
      return false;
    }
    String trimmed = expressionText.strip();
    // If the expression text appears literally in the source, it's plausible
    if (fullSource != null && fullSource.contains(trimmed)) {
      return true;
    }
    // Expression text not found in full source — likely mangled token text
    if (fullSource != null) {
      return false;
    }
    // A leading binary operator is suspicious
    if (trimmed.startsWith("+") || trimmed.startsWith("*") || trimmed.startsWith("/")) {
      return false;
    }
    return true;
  }

  /**
   * Extract the expression (after the last semicolon or last newline that follows a declaration).
   */
  private static String extractExpressionFromSource(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }
    // Find the last declaration boundary (semicolon or newline after description)
    int lastSemicolon = source.lastIndexOf(';');
    int lastNewline = source.lastIndexOf('\n');
    int boundary = Math.max(lastSemicolon, lastNewline);
    if (boundary >= 0 && boundary < source.length() - 1) {
      return source.substring(boundary + 1).strip();
    }
    return null;
  }
}
