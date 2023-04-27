package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class StringVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    StringVariableParser parser = new StringVariableParser();
    
    testUnMatch(parser, "$foo");
    testAllMatch(parser, "$foo as string");
    testAllMatch(parser, "$foo as String");
    testAllMatch(parser, "(string)$foo");
    testAllMatch(parser, "(String)$foo");
    testUnMatch(parser, "$foo as boolean");
  }

}
