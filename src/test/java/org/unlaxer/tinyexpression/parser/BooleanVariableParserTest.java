package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.bool.BooleanVariableParser;

public class BooleanVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    BooleanVariableParser parser = new BooleanVariableParser();
    
    testUnMatch(parser, "$foo");
    testAllMatch(parser, "$foo as boolean");
    testAllMatch(parser, "$foo as Boolean");
    testAllMatch(parser, "(boolean)$foo");
    testAllMatch(parser, "(Boolean)$foo");
    testUnMatch(parser, "$foo as String");
  }
}
