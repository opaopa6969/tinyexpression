package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableParser;

public class NumberVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    NumberVariableParser parser = new NumberVariableParser();
    
    testUnMatch(parser, "$foo");
    testAllMatch(parser, "$foo as number");
    testAllMatch(parser, "$foo as Number");
    testAllMatch(parser, "$foo as float");
    testAllMatch(parser, "$foo as Float");
    testAllMatch(parser, "(number)$foo");
    testAllMatch(parser, "(Number)$foo");
    testAllMatch(parser, "(float)$foo");
    testAllMatch(parser, "(Float)$foo");
    testUnMatch(parser, "$foo as String");

  }

}
