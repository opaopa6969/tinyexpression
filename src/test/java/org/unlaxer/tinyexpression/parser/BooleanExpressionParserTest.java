package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;

public class BooleanExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    BooleanExpressionParser parser = new BooleanExpressionParser();
    
    testAllMatch(parser, "true");
    testAllMatch(parser, "true | false ");
    testAllMatch(parser, "true & $foo == 1");
    testAllMatch(parser, "$hour>0 & $hour<5");
  }

}
