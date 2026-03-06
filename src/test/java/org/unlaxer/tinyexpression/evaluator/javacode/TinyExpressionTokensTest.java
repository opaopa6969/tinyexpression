package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.NumberParser;

public class TinyExpressionTokensTest {

  @Test
  public void testRejectsNullTinyExpressionToken() {
    SpecifiedExpressionTypes specifiedExpressionTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);

    try {
      new TinyExpressionTokens(null, specifiedExpressionTypes);
      fail("expected null tinyExpressionToken failure");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("must not be null"));
    }
  }

  @Test
  public void testRejectsNonTinyExpressionParserToken() {
    NumberParser numberParser = new NumberParser();
    ParseContext parseContext = new ParseContext(new StringSource("123"));
    Parsed parsed = numberParser.parse(parseContext);
    Token numberToken = parsed.getRootToken(true);

    SpecifiedExpressionTypes specifiedExpressionTypes =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);

    try {
      new TinyExpressionTokens(numberToken, specifiedExpressionTypes);
      fail("expected parser type failure");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains(NumberParser.class.getName()));
    }
  }
}
