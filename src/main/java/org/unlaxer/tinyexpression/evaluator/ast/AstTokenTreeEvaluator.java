package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.elementary.QuotedParser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;
import org.unlaxer.tinyexpression.function.EmbeddedFunction;
import org.unlaxer.tinyexpression.parser.*;
import org.unlaxer.tinyexpression.parser.booltype.BooleanFactorParser;
import org.unlaxer.tinyexpression.parser.function.*;
import org.unlaxer.util.MultipleParamterStringPredicators;
import org.unlaxer.util.FactoryBoundCache;

/**
 * Evaluates a TinyExpression formula by walking the parsed/reduced token tree directly,
 * without generating Java code.
 */
final class AstTokenTreeEvaluator {

  private AstTokenTreeEvaluator() {}

  /**
   * Parse, reduce, and evaluate a formula string. Returns the result as a Number, Boolean, or String.
   */
  static Optional<Object> tryEvaluate(String source, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    try {
      Token rootToken = parseAndReduce(source);
      TinyExpressionTokens tokens = new TinyExpressionTokens(rootToken, specifiedExpressionTypes);
      Token expressionToken = tokens.getExpressionToken();
      ExpressionType numberType = resolveNumberType(specifiedExpressionTypes);
      Object result = evaluateAny(expressionToken, numberType, specifiedExpressionTypes, calculationContext);
      return Optional.ofNullable(result);
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  private static Token parseAndReduce(String source) {
    Parser parser = Parser.get(TinyExpressionParser.class);
    ParseContext parseContext = new ParseContext(new StringSource(source));
    try (parseContext) {
      Parsed parsed = parser.parse(parseContext);
      if (!parsed.isSucceeded()) {
        throw new IllegalArgumentException("Parse failed for AST token tree evaluator");
      }
      Token rootToken = parsed.getRootToken(true);
      rootToken = VariableTypeResolver.resolveVariableType(rootToken);
      return OperatorOperandTreeCreator.SINGLETON.apply(rootToken);
    }
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

  // ---- top-level dispatch ----

  /**
   * Evaluate any token node, returning Number, Boolean, or String.
   */
  private static Object evaluateAny(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    if (token == null) {
      throw new IllegalArgumentException("Token is null");
    }
    Parser parser = token.parser;

    // Unwrap expression/term/factor wrappers
    token = unwrapExpression(token);
    parser = token.parser;

    // ---- Number literals ----
    if (parser instanceof NumberParser) {
      return numberType.parseNumber(token.tokenString.get());
    }

    // ---- Boolean literals ----
    if (parser instanceof TrueTokenParser) {
      return true;
    }
    if (parser instanceof FalseTokenParser) {
      return false;
    }

    // ---- String literals ----
    if (parser instanceof StringLiteralParser) {
      return evaluateStringLiteral(token);
    }

    // ---- Variables ----
    if (parser instanceof NumberVariableParser) {
      return evaluateNumberVariable(token, numberType, types, ctx);
    }
    if (parser instanceof NakedVariableParser) {
      return evaluateNakedVariable(token, numberType, types, ctx);
    }
    if (parser instanceof BooleanVariableParser) {
      return evaluateBooleanVariable(token, ctx);
    }
    if (parser instanceof StringVariableParser) {
      return evaluateStringVariable(token, ctx);
    }

    // ---- Binary arithmetic operators ----
    if (parser instanceof PlusParser || parser instanceof MinusParser
        || parser instanceof MultipleParser || parser instanceof DivisionParser) {
      return evaluateBinaryArithmetic(token, numberType, types, ctx);
    }

    // ---- Math functions ----
    if (parser instanceof SinParser) {
      Number arg = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      double result = Math.sin(ctx.radianAngle(arg.doubleValue()));
      return castToNumberType(result, numberType);
    }
    if (parser instanceof CosParser) {
      Number arg = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      double result = Math.cos(ctx.radianAngle(arg.doubleValue()));
      return castToNumberType(result, numberType);
    }
    if (parser instanceof TanParser) {
      Number arg = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      double result = Math.tan(ctx.radianAngle(arg.doubleValue()));
      return castToNumberType(result, numberType);
    }
    if (parser instanceof SquareRootParser) {
      Number arg = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      double result = Math.sqrt(arg.doubleValue());
      return castToNumberType(result, numberType);
    }

    // ---- Min / Max ----
    if (parser instanceof MinParser) {
      Number left = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      Number right = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
      double result = Math.min(left.doubleValue(), right.doubleValue());
      return castToNumberType(result, numberType);
    }
    if (parser instanceof MaxParser) {
      Number left = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
      Number right = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
      double result = Math.max(left.doubleValue(), right.doubleValue());
      return castToNumberType(result, numberType);
    }

    // ---- Random ----
    if (parser instanceof RandomParser) {
      return castToNumberType(ctx.nextRandom(), numberType);
    }

    // ---- Number if expression ----
    if (parser instanceof NumberIfExpressionParser) {
      return evaluateNumberIf(token, numberType, types, ctx);
    }

    // ---- Boolean if expression ----
    if (parser instanceof BooleanIfExpressionParser) {
      return evaluateBooleanIf(token, numberType, types, ctx);
    }

    // ---- String if expression ----
    if (parser instanceof StringIfExpressionParser) {
      return evaluateStringIf(token, numberType, types, ctx);
    }

    // ---- Number match expression ----
    if (parser instanceof NumberMatchExpressionParser) {
      return evaluateNumberMatch(token, numberType, types, ctx);
    }

    // ---- Boolean match expression ----
    if (parser instanceof BooleanMatchExpressionParser) {
      return evaluateBooleanMatch(token, numberType, types, ctx);
    }

    // ---- String match expression ----
    if (parser instanceof StringMatchExpressionParser) {
      return evaluateStringMatch(token, numberType, types, ctx);
    }

    // ---- Boolean expression (AND/OR/XOR chains) ----
    if (parser instanceof BooleanExpressionParser
        || parser instanceof StrictTypedBooleanExpressionParser) {
      return evaluateBooleanExpression(token, numberType, types, ctx);
    }

    // ---- OR / AND / XOR ----
    if (parser instanceof OrParser) {
      boolean left = evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
      boolean right = evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
      return left || right;
    }
    if (parser instanceof AndParser) {
      boolean left = evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
      boolean right = evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
      return left && right;
    }
    if (parser instanceof XorParser) {
      boolean left = evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
      boolean right = evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
      return left ^ right;
    }
    if (parser instanceof EqualEqualParser) {
      boolean left = evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
      boolean right = evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
      return left == right;
    }
    if (parser instanceof NotEqualParser) {
      boolean left = evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
      boolean right = evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
      return left != right;
    }

    // ---- Not ----
    if (parser instanceof NotBooleanExpressionParser) {
      boolean value = evaluateBoolean(token.filteredChildren.get(0), numberType, types, ctx);
      return !value;
    }

    // ---- isPresent ----
    if (parser instanceof IsPresentParser) {
      Token variableToken = token.filteredChildren.get(0);
      String varName = extractVariableName(variableToken);
      return ctx.isExists(varName);
    }

    // ---- inTimeRange ----
    if (parser instanceof InTimeRangeParser) {
      float fromHour = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx).floatValue();
      float toHour = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx).floatValue();
      return EmbeddedFunction.inTimeRange(ctx, fromHour, toHour);
    }

    // ---- inDayTimeRange ----
    if (parser instanceof InDayTimeRangeParser) {
      String fromDay = token.filteredChildren.get(0).tokenString.get().trim();
      float fromHour = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx).floatValue();
      String toDay = token.filteredChildren.get(2).tokenString.get().trim();
      float toHour = evaluateNumber(token.filteredChildren.get(3), numberType, types, ctx).floatValue();
      return ctx.inDayTimeRange(
          java.time.DayOfWeek.valueOf(fromDay), fromHour,
          java.time.DayOfWeek.valueOf(toDay), toHour);
    }

    // ---- ToNum ----
    if (parser instanceof ToNumParser) {
      String stringValue = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      Number defaultValue = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
      try {
        return numberType.parseNumber(stringValue);
      } catch (Throwable ignored) {
        return defaultValue;
      }
    }

    // ---- Number comparisons ----
    if (parser instanceof NumberEqualEqualExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) == 0;
    }
    if (parser instanceof NumberNotEqualExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) != 0;
    }
    if (parser instanceof NumberGreaterExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) > 0;
    }
    if (parser instanceof NumberGreaterOrEqualExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) >= 0;
    }
    if (parser instanceof NumberLessExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) < 0;
    }
    if (parser instanceof NumberLessOrEqualExpressionParser) {
      return compareNumbers(token, numberType, types, ctx) <= 0;
    }

    // ---- String equality ----
    if (parser instanceof StringEqualsExpressionParser) {
      String left = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      String right = evaluateString(token.filteredChildren.get(1), numberType, types, ctx);
      return left.equals(right);
    }
    if (parser instanceof StringNotEqualsExpressionParser) {
      String left = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      String right = evaluateString(token.filteredChildren.get(1), numberType, types, ctx);
      return !left.equals(right);
    }

    // ---- String multiple parameter predicators (in, startsWith, endsWith, contains) ----
    if (parser instanceof StringMultipleParameterPredicator) {
      return evaluateStringPredicator(token, numberType, types, ctx);
    }

    // ---- String concatenation (StringPlusParser) ----
    if (parser instanceof StringPlusParser) {
      return evaluateStringPlus(token, numberType, types, ctx);
    }

    // ---- String operations ----
    if (parser instanceof TrimParser) {
      String inner = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      return inner.trim();
    }
    if (parser instanceof ToUpperCaseParser) {
      String inner = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      return inner.toUpperCase();
    }
    if (parser instanceof ToLowerCaseParser) {
      String inner = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      return inner.toLowerCase();
    }

    // ---- String slice ----
    if (parser instanceof SliceParser) {
      return evaluateStringSlice(token, numberType, types, ctx);
    }

    // ---- String length ----
    if (parser instanceof StringLengthParser) {
      String inner = evaluateString(token.filteredChildren.get(0), numberType, types, ctx);
      return castToNumberType(inner.length(), numberType);
    }

    // ---- Side effect / external formula ----
    if (parser instanceof SideEffectExpressionParser
        || parser instanceof BooleanSideEffectExpressionParser
        || parser instanceof StringSideEffectExpressionParser) {
      throw new UnsupportedOperationException(
          "external formulas not yet supported in AST evaluator");
    }

    // ---- Method invocation ----
    if (parser instanceof MethodInvocationParser) {
      throw new UnsupportedOperationException(
          "method invocations not yet supported in AST evaluator");
    }

    throw new UnsupportedOperationException(
        "AstTokenTreeEvaluator: unsupported parser type: " + parser.getClass().getName());
  }

  // ---- typed evaluation helpers ----

  private static Number evaluateNumber(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Object result = evaluateAny(token, numberType, types, ctx);
    if (result instanceof Number n) {
      return n;
    }
    if (result instanceof Boolean b) {
      return castToNumberType(b ? 1.0 : 0.0, numberType);
    }
    throw new IllegalArgumentException("Expected number but got: " + result);
  }

  private static boolean evaluateBoolean(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Object result = evaluateAny(token, numberType, types, ctx);
    if (result instanceof Boolean b) {
      return b;
    }
    if (result instanceof Number n) {
      return n.doubleValue() != 0.0;
    }
    throw new IllegalArgumentException("Expected boolean but got: " + result);
  }

  private static String evaluateString(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Object result = evaluateAny(token, numberType, types, ctx);
    if (result instanceof String s) {
      return s;
    }
    return String.valueOf(result);
  }

  // ---- unwrap expression wrappers ----

  private static Token unwrapExpression(Token token) {
    Parser parser = token.parser;
    // Unwrap single-child expression/term/factor wrappers
    if (parser instanceof NumberExpressionParser
        || parser instanceof StrictTypedNumberExpressionParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof NumberTermParser
        || parser instanceof StrictTypedNumberTermParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof NumberFactorParser
        || parser instanceof StrictTypedNumberFactorParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof BooleanExpressionParser
        || parser instanceof StrictTypedBooleanExpressionParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof BooleanFactorParser
        || parser instanceof StrictTypedBooleanFactorParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof StringExpressionParser
        || parser instanceof StrictTypedStringExpressionParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof StringTermParser
        || parser instanceof StrictTypedStringTermParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof StringFactorParser
        || parser instanceof StrictTypedStringFactorParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    if (parser instanceof ObjectExpressionParser) {
      if (!token.filteredChildren.isEmpty() && token.filteredChildren.size() == 1) {
        return unwrapExpression(token.filteredChildren.get(0));
      }
    }
    return token;
  }

  // ---- binary arithmetic ----

  private static Number evaluateBinaryArithmetic(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    // After OperatorOperandTreeCreator, binary ops have: [operator, left, right]
    Number left = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
    Number right = evaluateNumber(token.filteredChildren.get(2), numberType, types, ctx);
    String op = getOperatorSymbol(token.parser);
    return applyBinary(op, left, right, numberType, ctx);
  }

  private static String getOperatorSymbol(Parser parser) {
    if (parser instanceof PlusParser) return "+";
    if (parser instanceof MinusParser) return "-";
    if (parser instanceof MultipleParser) return "*";
    if (parser instanceof DivisionParser) return "/";
    throw new IllegalArgumentException("Unknown arithmetic operator: " + parser.getClass().getName());
  }

  private static Number applyBinary(String operator, Number left, Number right,
      ExpressionType numberType, CalculationContext ctx) {
    if (numberType.isBigInteger()) {
      BigInteger l = (left instanceof BigInteger) ? (BigInteger) left : new BigInteger(left.toString());
      BigInteger r = (right instanceof BigInteger) ? (BigInteger) right : new BigInteger(right.toString());
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
        case "/" -> l.divide(r, ctx.scale(), ctx.roundingMode());
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
    // Default: float
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

  // ---- number comparisons ----

  private static int compareNumbers(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Number left = evaluateNumber(token.filteredChildren.get(0), numberType, types, ctx);
    Number right = evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
    return Float.compare(left.floatValue(), right.floatValue());
  }

  // ---- if expressions ----

  private static Number evaluateNumberIf(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    // After OperatorOperandTreeCreator: filteredChildren = [boolExpr, thenExpr, elseExpr]
    boolean condition = evaluateBoolean(token.filteredChildren.get(0), numberType, types, ctx);
    if (condition) {
      return evaluateNumber(token.filteredChildren.get(1), numberType, types, ctx);
    } else {
      return evaluateNumber(token.filteredChildren.get(2), numberType, types, ctx);
    }
  }

  private static boolean evaluateBooleanIf(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    boolean condition = evaluateBoolean(token.filteredChildren.get(0), numberType, types, ctx);
    if (condition) {
      return evaluateBoolean(token.filteredChildren.get(1), numberType, types, ctx);
    } else {
      return evaluateBoolean(token.filteredChildren.get(2), numberType, types, ctx);
    }
  }

  private static String evaluateStringIf(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    boolean condition = evaluateBoolean(token.filteredChildren.get(0), numberType, types, ctx);
    if (condition) {
      return evaluateString(token.filteredChildren.get(1), numberType, types, ctx);
    } else {
      return evaluateString(token.filteredChildren.get(2), numberType, types, ctx);
    }
  }

  // ---- match expressions ----

  private static Number evaluateNumberMatch(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    // filteredChildren: [caseExpressionToken, defaultToken]
    Token caseExpression = token.filteredChildren.get(0);
    Token defaultExpression = token.filteredChildren.get(1);

    for (Token caseFactor : caseExpression.filteredChildren) {
      boolean condition = evaluateBoolean(caseFactor.filteredChildren.get(0), numberType, types, ctx);
      if (condition) {
        return evaluateNumber(caseFactor.filteredChildren.get(1), numberType, types, ctx);
      }
    }
    return evaluateNumber(defaultExpression, numberType, types, ctx);
  }

  private static boolean evaluateBooleanMatch(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Token caseExpression = token.filteredChildren.get(0);
    Token defaultExpression = token.filteredChildren.get(1);

    for (Token caseFactor : caseExpression.filteredChildren) {
      boolean condition = evaluateBoolean(caseFactor.filteredChildren.get(0), numberType, types, ctx);
      if (condition) {
        return evaluateBoolean(caseFactor.filteredChildren.get(1), numberType, types, ctx);
      }
    }
    return evaluateBoolean(defaultExpression, numberType, types, ctx);
  }

  private static String evaluateStringMatch(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    Token caseExpression = token.filteredChildren.get(0);
    Token defaultExpression = token.filteredChildren.get(1);

    for (Token caseFactor : caseExpression.filteredChildren) {
      boolean condition = evaluateBoolean(caseFactor.filteredChildren.get(0), numberType, types, ctx);
      if (condition) {
        return evaluateString(caseFactor.filteredChildren.get(1), numberType, types, ctx);
      }
    }
    return evaluateString(defaultExpression, numberType, types, ctx);
  }

  // ---- boolean expression (AND/OR/XOR chains) ----

  private static boolean evaluateBooleanExpression(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    List<Token> children = token.filteredChildren;
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty boolean expression");
    }
    if (children.size() == 1) {
      return evaluateBoolean(children.get(0), numberType, types, ctx);
    }
    // After OperatorOperandTreeCreator, the boolean expression chains become
    // nested operator nodes. If we see a single child, evaluate it.
    // If we see multiple, it's the alternating: factor, operator, factor, ...
    // But after tree creation these should be nested binary trees.
    // Evaluate each child as boolean and combine (the tree creator already built
    // binary tree structure for OR/AND/XOR).
    boolean result = evaluateBoolean(children.get(0), numberType, types, ctx);
    return result;
  }

  // ---- variables ----

  private static Number evaluateNumberVariable(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    String varName = extractVariableName(token);
    Optional<Float> value = ctx.getValue(varName);
    if (value.isPresent()) {
      return castToNumberType(value.get(), numberType);
    }
    // default: zero
    return castToNumberType(0.0, numberType);
  }

  private static boolean evaluateBooleanVariable(Token token, CalculationContext ctx) {
    String varName = extractVariableName(token);
    return ctx.getBoolean(varName).orElse(false);
  }

  private static String evaluateStringVariable(Token token, CalculationContext ctx) {
    String varName = extractVariableName(token);
    return ctx.getString(varName).orElse("");
  }

  /**
   * Evaluate a NakedVariableParser token by trying all variable types in order:
   * string, boolean, number. Returns whatever type the context has stored for this variable.
   * This is needed because NakedVariableParser doesn't carry type information —
   * the type depends on how the variable was set in the CalculationContext.
   */
  private static Object evaluateNakedVariable(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    String varName = extractVariableName(token);
    // Try string first (for string comparison contexts)
    Optional<String> stringValue = ctx.getString(varName);
    if (stringValue.isPresent()) {
      return stringValue.get();
    }
    // Try boolean
    Optional<Boolean> booleanValue = ctx.getBoolean(varName);
    if (booleanValue.isPresent()) {
      return booleanValue.get();
    }
    // Fall back to number (most common case)
    Optional<Float> numberValue = ctx.getValue(varName);
    if (numberValue.isPresent()) {
      return castToNumberType(numberValue.get(), numberType);
    }
    // default: zero (backward compatible with NumberVariableParser behavior)
    return castToNumberType(0.0, numberType);
  }

  private static String extractVariableName(Token token) {
    // VariableParser stores the name. Try typed access first.
    if (token.parser instanceof VariableParser variableParser) {
      try {
        return variableParser.getVariableName(token.typed(VariableParser.class));
      } catch (Throwable ignored) {
        // fall through
      }
    }
    // Fallback: extract from token string
    String raw = token.tokenString.get();
    if (raw != null && raw.startsWith("$")) {
      // Extract just the variable name (everything after $ until non-alphanumeric)
      int end = 1;
      while (end < raw.length()) {
        char c = raw.charAt(end);
        if (Character.isLetterOrDigit(c) || c == '_') {
          end++;
        } else {
          break;
        }
      }
      return raw.substring(1, end);
    }
    return raw;
  }

  // ---- string literals ----

  private static String evaluateStringLiteral(Token token) {
    Token literalChoiceToken = ChoiceInterface.choiced(token);
    Source contents = stringByToken.get(literalChoiceToken);
    String raw = contents == null ? "" : contents.sourceAsString();
    return normalizeLiteralContents(raw);
  }

  private static final FactoryBoundCache<Token, Source> stringByToken =
      new FactoryBoundCache<>(QuotedParser::contents);

  private static String normalizeLiteralContents(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    if (raw.length() >= 2) {
      char start = raw.charAt(0);
      char end = raw.charAt(raw.length() - 1);
      if ((start == '\'' && end == '\'') || (start == '"' && end == '"')) {
        return raw.substring(1, raw.length() - 1);
      }
    }
    char start = raw.charAt(0);
    if (start == '\'' || start == '"') {
      return raw.substring(1);
    }
    char end = raw.charAt(raw.length() - 1);
    if (end == '\'' || end == '"') {
      return raw.substring(0, raw.length() - 1);
    }
    return raw;
  }

  // ---- string concatenation ----

  private static String evaluateStringPlus(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    // StringPlusParser after tree creator: [operator, ...children]
    // Actually, after OperatorOperandTreeCreator stringTerm processing,
    // StringPlusParser gets: operator.newCreatesOf(operator, lastOperatorAndOperands)
    // So filteredChildren[0] is the operator token itself, and rest are operands
    StringBuilder sb = new StringBuilder();
    List<Token> children = token.filteredChildren;
    // Skip the first child (operator marker), evaluate the rest
    for (int i = 1; i < children.size(); i++) {
      sb.append(evaluateString(children.get(i), numberType, types, ctx));
    }
    return sb.toString();
  }

  // ---- string slice ----

  private static String evaluateStringSlice(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    // SliceParser: filteredChildren[0] = slicer spec, filteredChildren[1] = string expression
    Token stringFactorToken = token.filteredChildren.get(1);
    Token slicerToken = token.filteredChildren.get(0);
    String inner = evaluateString(stringFactorToken, numberType, types, ctx);

    Optional<String> specifier = slicerToken.getToken()
        .map(wrapped -> wrapped.substring(1, wrapped.length() - 1));

    if (specifier.isPresent()) {
      return new org.unlaxer.util.Slicer(new StringSource(inner))
          .pythonian(specifier.get()).get().sourceAsString();
    }
    return inner;
  }

  // ---- string predicators (in, startsWith, endsWith, contains) ----

  private static boolean evaluateStringPredicator(Token token, ExpressionType numberType,
      SpecifiedExpressionTypes types, CalculationContext ctx) {
    StringMultipleParameterPredicator predicator =
        (StringMultipleParameterPredicator) token.parser;
    List<Token> children = token.filteredChildren;
    // After OperatorOperandTreeCreator: filteredChildren = [leftExpr, param1, param2, ...]
    if (children.isEmpty()) {
      return false;
    }
    String base = evaluateString(children.get(0), numberType, types, ctx);
    String[] params = new String[children.size() - 1];
    for (int i = 1; i < children.size(); i++) {
      params[i - 1] = evaluateString(children.get(i), numberType, types, ctx);
    }

    if (predicator instanceof StringInParser) {
      return MultipleParamterStringPredicators.in(base, params);
    }
    if (predicator instanceof StringStartsWithParser) {
      return MultipleParamterStringPredicators.startsWith(base, params);
    }
    if (predicator instanceof StringEndsWithParser) {
      return MultipleParamterStringPredicators.endsWith(base, params);
    }
    if (predicator instanceof StringContainsParser) {
      return MultipleParamterStringPredicators.contains(base, params);
    }

    throw new UnsupportedOperationException(
        "Unsupported string predicator: " + predicator.getClass().getName());
  }

  // ---- number type conversion ----

  private static Number castToNumberType(double value, ExpressionType numberType) {
    if (numberType.isBigDecimal()) {
      return BigDecimal.valueOf(value);
    }
    if (numberType.isBigInteger()) {
      return BigInteger.valueOf((long) value);
    }
    if (numberType.isDouble()) {
      return value;
    }
    if (numberType.isInt()) {
      return (int) value;
    }
    if (numberType.isLong()) {
      return (long) value;
    }
    // Default: float
    return (float) value;
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value == null) return BigDecimal.ZERO;
    if (value instanceof BigDecimal bd) return bd;
    return new BigDecimal(value.toString());
  }
}
