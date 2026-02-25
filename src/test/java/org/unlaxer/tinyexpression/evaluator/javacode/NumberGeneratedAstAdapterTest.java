package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstField;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNode;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstNodeKind;
import org.unlaxer.tinyexpression.ast.annotation.TinyAstOperator;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstAdapter;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstNode;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.PlusParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public class NumberGeneratedAstAdapterTest {

  @Test
  public void testGeneratedNumberAstPathBuildsBinaryExpression() {
    TinyExpressionTokens tinyExpressionTokens = tinyExpressionTokens("1+(8/4)");
    Token normalizedExpression = NumberExpressionBuilder.SINGLETON
        .unwrapNumberExpressionToken(tinyExpressionTokens.expressionToken);

    NumberGeneratedAstNode generatedAst = NumberGeneratedAstAdapter.SINGLETON
        .generateOrThrow(normalizedExpression);

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    NumberExpressionBuilder.SINGLETON.build(builder, generatedAst, tinyExpressionTokens);
    String javaCode = builder.build();

    assertTrue(javaCode.contains("(1.0f+(8.0f/4.0f))"));
  }

  @Test
  public void testGeneratedNumberAstFailsWithInvalidChildMapping() {
    TinyExpressionTokens tinyExpressionTokens = tinyExpressionTokens("1+2");
    Token normalizedExpression = NumberExpressionBuilder.SINGLETON
        .unwrapNumberExpressionToken(tinyExpressionTokens.expressionToken);

    normalizedExpression.replace(Parser.get(BrokenAstPlusParser.class));

    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> NumberGeneratedAstAdapter.SINGLETON.generateOrThrow(normalizedExpression));
    assertTrue(exception.getMessage().contains("childIndex"));
  }

  private TinyExpressionTokens tinyExpressionTokens(String expression) {
    TinyExpressionParser parser = new TinyExpressionParser();
    ParseContext parseContext = new ParseContext(new StringSource(expression));
    Parsed parsed = parser.parse(parseContext);
    Token rootToken = parsed.getRootToken(true);
    rootToken = OperatorOperandTreeCreator.SINGLETON.apply(rootToken);

    SpecifiedExpressionTypes specifiedExpressionTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    return new TinyExpressionTokens(rootToken, specifiedExpressionTypes);
  }

  @TinyAstNode(kind = TinyAstNodeKind.NUMBER_BINARY)
  @TinyAstOperator(symbol = "+")
  @TinyAstField(name = "left", childIndex = 99)
  @TinyAstField(name = "right", childIndex = 2)
  public static class BrokenAstPlusParser extends PlusParser {
    private static final long serialVersionUID = 1L;
  }
}
