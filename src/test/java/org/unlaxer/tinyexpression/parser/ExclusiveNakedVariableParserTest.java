package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class ExclusiveNakedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.mostDetail);
    
    var parser = new ExclusiveNakedVariableParser();
    testAllMatch(parser, "$foo");
    testUnMatch(parser, "$foo as String");
    testUnMatch(parser, "(String) $foo");
  }

}
