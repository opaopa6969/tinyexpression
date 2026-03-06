package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstAdapter;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstNode;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedBinaryAstNode;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedLiteralAstNode;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.NumberTermParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

final class AstNumberExpressionEvaluator {

  private AstNumberExpressionEvaluator() {}

  static Optional<Object> tryEvaluate(String source, SpecifiedExpressionTypes specifiedExpressionTypes,
      CalculationContext calculationContext) {
    try {
      Token rootToken = parseAndReduce(source);
      TinyExpressionTokens tokens = new TinyExpressionTokens(rootToken, specifiedExpressionTypes);
      Token normalized = unwrapNumberExpressionToken(tokens.getExpressionToken());
      Optional<NumberGeneratedAstNode> ast = NumberGeneratedAstAdapter.SINGLETON.tryGenerate(normalized);
      if (ast.isEmpty()) {
        return Optional.empty();
      }
      ExpressionType numberType = resolveNumberType(specifiedExpressionTypes);
      Number value = evaluate(ast.get(), numberType, calculationContext);
      return Optional.of(value);
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
        throw new IllegalArgumentException("Parse failed for AST evaluator path");
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

  private static Token unwrapNumberExpressionToken(Token token) {
    if (token == null) {
      return null;
    }
    if (token.parser instanceof NumberExpressionParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    if (token.parser instanceof NumberTermParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    if (token.parser instanceof NumberFactorParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    return token;
  }

  private static Number evaluate(NumberGeneratedAstNode node, ExpressionType numberType,
      CalculationContext calculationContext) {
    if (node instanceof NumberGeneratedLiteralAstNode literal) {
      return numberType.parseNumber(literal.literal());
    }
    if (node instanceof NumberGeneratedBinaryAstNode binary) {
      Number left = evaluate(binary.left(), numberType, calculationContext);
      Number right = evaluate(binary.right(), numberType, calculationContext);
      return applyBinary(binary.operator(), left, right, numberType, calculationContext);
    }
    throw new IllegalArgumentException("Unsupported generated number AST node: " + node.getClass().getName());
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
