package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.string.StrictTypedStringExpressionParser;

public class StrictTypedStringExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.mostDetail);
//    setLevel(OutputLevel.detail);
    
    var parser = new StrictTypedStringExpressionParser();
    
    testAllMatch(parser, "'string'");
    testAllMatch(parser, "'string'[0:]");
    testUnMatch(parser, "$hode");
    testAllMatch(parser, "$hode as String");
    testAllMatch(parser, "$hode as string");
    testAllMatch(parser, "$hode string");
    testAllMatch(parser, "(String)$hode");
    
  }

}
