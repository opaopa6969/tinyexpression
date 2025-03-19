package org.unlaxer.tinyexpression.parser.numbertype;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.numbertype.NumberClassParser.NumberWordParser;

public class NumberTypeHintParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);

    NumberTypeHintParser parser = new NumberTypeHintParser();

    testAllMatch(parser,"double");
    testAllMatch(parser,"Float");
    testAllMatch(parser,"int");
    testAllMatch(parser,"number");
    testAllMatch(parser,"BigDecimal");
    TestResult testAllMatch = testAllMatch(parser,"Long");

    Token rootToken = testAllMatch.parsed.getRootToken();

    TokenPrinter.output(rootToken, System.out);

    List<Token> originalTokens = testAllMatch.parsed.getOriginalTokens();

    System.out.println("original tokens:");

    for (Token token : originalTokens) {
      System.out.println();
      TokenPrinter.output(token, System.out);
    }

    NumberWordParser parser2 = rootToken.getParser(NumberWordParser.class);
    ExpressionType expressionType = parser2.expressionType;

    assertEquals(ExpressionTypes._long, expressionType);

    testUnMatch(parser,"String");
    testUnMatch(parser,"word");
  }

}
