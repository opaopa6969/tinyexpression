package org.unlaxer.tinyexpression.evaluator.javacode;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

final class DslGeneratedAstJavaEmitter {

  record EmittedJava(String javaCode, String mode) {}

  private DslGeneratedAstJavaEmitter() {}

  static Optional<EmittedJava> tryEmit(String className, Source source,
      SpecifiedExpressionTypes specifiedExpressionTypes, ClassLoader classLoader) {
    if (className == null || className.isBlank() || source == null || specifiedExpressionTypes == null) {
      return Optional.empty();
    }
    ExpressionType resultType = specifiedExpressionTypes.resultType();
    if (resultType == null) {
      return Optional.empty();
    }
    String formula = source.source() == null ? "" : source.source().strip();
    if (formula.isEmpty() || !isLiteralEligible(formula, resultType)) {
      return Optional.empty();
    }

    Optional<Object> parsed = tryParseAst(formula, preferredAstSimpleName(resultType), classLoader);
    if (parsed.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> expression = renderExpression(parsed.get(), resultType, specifiedExpressionTypes);
    if (expression.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new EmittedJava(buildJavaClass(className, resultType, expression.get()), "native-generated-ast"));
  }

  private static boolean isLiteralEligible(String formula, ExpressionType resultType) {
    if (formula == null) {
      return false;
    }
    String text = formula.strip();
    if (text.isEmpty()) {
      return false;
    }
    if (text.contains("\n") || text.contains(";") || text.contains("{") || text.contains("}")) {
      return false;
    }
    if (text.startsWith("$")) {
      return false;
    }
    if (resultType.isNumber()) {
      return isNumberLiteral(text);
    }
    if (resultType.isString()) {
      return isStringLiteral(text);
    }
    if (resultType.isBoolean()) {
      return isBooleanLiteral(text);
    }
    if (resultType.isObject()) {
      return isNumberLiteral(text) || isStringLiteral(text) || isBooleanLiteral(text);
    }
    return false;
  }

  private static boolean isNumberLiteral(String text) {
    return text != null && text.matches("[-+]?\\d+(?:\\.\\d+)?");
  }

  private static boolean isStringLiteral(String text) {
    return text != null
        && text.length() >= 2
        && ((text.charAt(0) == '\'' && text.charAt(text.length() - 1) == '\'')
            || (text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"'));
  }

  private static boolean isBooleanLiteral(String text) {
    return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
  }

  private static String preferredAstSimpleName(ExpressionType resultType) {
    if (resultType.isNumber()) {
      return "BinaryExpr";
    }
    if (resultType.isString()) {
      return "StringExpr";
    }
    if (resultType.isBoolean()) {
      return "BooleanExpr";
    }
    return "ObjectExpr";
  }

  private static Optional<Object> tryParseAst(String formula, String preferredAstSimpleName, ClassLoader classLoader) {
    ClassLoader effectiveClassLoader =
        classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    if (effectiveClassLoader == null) {
      effectiveClassLoader = DslGeneratedAstJavaEmitter.class.getClassLoader();
    }
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, effectiveClassLoader);
      try {
        Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
        Object ast = parsePreferred.invoke(null, formula, preferredAstSimpleName);
        return Optional.ofNullable(ast);
      } catch (NoSuchMethodException ignored) {
        Method parse = mapperClass.getMethod("parse", String.class);
        Object ast = parse.invoke(null, formula);
        return Optional.ofNullable(ast);
      }
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private static Optional<String> renderExpression(Object ast, ExpressionType resultType,
      SpecifiedExpressionTypes specifiedExpressionTypes) {
    if (ast == null || resultType == null) {
      return Optional.empty();
    }
    String simpleName = ast.getClass().getSimpleName();
    if ("ExpressionExpr".equals(simpleName)) {
      Object unwrapped = readComponent(ast, "value");
      if (unwrapped != null) {
        ast = unwrapped;
        simpleName = ast.getClass().getSimpleName();
      }
    }
    if (resultType.isNumber()) {
      if (!"BinaryExpr".equals(simpleName)) {
        return Optional.empty();
      }
      ExpressionType numberType = specifiedExpressionTypes.numberType() == null
          ? (resultType == ExpressionTypes.number ? ExpressionTypes._float : resultType)
          : specifiedExpressionTypes.numberType();
      return renderNumberExpressionFromBinary(ast, numberType);
    }
    if (resultType.isString()) {
      if (!"StringExpr".equals(simpleName)) {
        return Optional.empty();
      }
      return renderStringLiteral(ast);
    }
    if (resultType.isBoolean()) {
      if (!"BooleanExpr".equals(simpleName)) {
        return Optional.empty();
      }
      return renderBooleanLiteral(ast);
    }
    if (resultType.isObject()) {
      if (!"ObjectExpr".equals(simpleName)) {
        return Optional.empty();
      }
      Object value = readComponent(ast, "value");
      if (value == null) {
        return Optional.of("null");
      }
      String nested = value.getClass().getSimpleName();
      if ("BinaryExpr".equals(nested)) {
        return renderNumberExpressionFromBinary(value, ExpressionTypes._float);
      }
      if ("StringExpr".equals(nested)) {
        return renderStringLiteral(value);
      }
      if ("BooleanExpr".equals(nested)) {
        return renderBooleanLiteral(value);
      }
      if (value instanceof String text) {
        if (isBooleanLiteral(text)) {
          return Optional.of(String.valueOf(Boolean.parseBoolean(text)));
        }
        if (isNumberLiteral(text)) {
          return Optional.of(ExpressionTypes._float.numberWithSuffix(text));
        }
      }
      return Optional.empty();
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private static Optional<String> renderNumberExpressionFromBinary(Object binaryExpr, ExpressionType numberType) {
    Object left = readComponent(binaryExpr, "left");
    List<Object> op = (List<Object>) readComponent(binaryExpr, "op");
    List<Object> right = (List<Object>) readComponent(binaryExpr, "right");
    if (op == null || right == null) {
      return Optional.empty();
    }
    if (left != null && op.isEmpty() && right.isEmpty()) {
      return renderNumberExpressionFromBinary(left, numberType);
    }
    if (left == null && op.size() == 1 && right.isEmpty()) {
      String literal = String.valueOf(op.get(0)).strip();
      if (!isNumberLiteral(literal)) {
        return Optional.empty();
      }
      ExpressionType effectiveType = numberType == null ? ExpressionTypes._float : numberType;
      return Optional.of(effectiveType.numberWithSuffix(literal));
    }
    if (left == null || op.isEmpty() || right.isEmpty() || op.size() != right.size()) {
      return Optional.empty();
    }
    Optional<String> expression = renderNumberExpressionFromBinary(left, numberType);
    if (expression.isEmpty()) {
      return Optional.empty();
    }
    String built = expression.get();
    for (int i = 0; i < op.size(); i++) {
      String operator = String.valueOf(op.get(i)).strip();
      if (!"+".equals(operator) && !"-".equals(operator) && !"*".equals(operator) && !"/".equals(operator)) {
        return Optional.empty();
      }
      Optional<String> rightExpression = renderNumberExpressionFromBinary(right.get(i), numberType);
      if (rightExpression.isEmpty()) {
        return Optional.empty();
      }
      built = "(" + built + operator + rightExpression.get() + ")";
    }
    return Optional.of(built);
  }

  private static Optional<String> renderStringLiteral(Object stringExpr) {
    Object value = readComponent(stringExpr, "value");
    if (!(value instanceof String text)) {
      return Optional.empty();
    }
    return Optional.of("\"" + escapeJavaString(text) + "\"");
  }

  private static Optional<String> renderBooleanLiteral(Object booleanExpr) {
    Object value = readComponent(booleanExpr, "value");
    if (value instanceof String text && isBooleanLiteral(text)) {
      return Optional.of(String.valueOf(Boolean.parseBoolean(text)));
    }
    if (value instanceof Boolean bool) {
      return Optional.of(String.valueOf(bool));
    }
    return Optional.empty();
  }

  private static Object readComponent(Object target, String componentName) {
    if (target == null || componentName == null || componentName.isBlank()) {
      return null;
    }
    try {
      Method accessor = target.getClass().getMethod(componentName);
      return accessor.invoke(target);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static String escapeJavaString(String raw) {
    StringBuilder builder = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        default -> builder.append(c);
      }
    }
    return builder.toString();
  }

  private static String buildJavaClass(String className, ExpressionType resultType, String expression) {
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
}
