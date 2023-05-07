package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class BooleanClauseParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    BooleanClauseParser parser = new BooleanClauseParser();
    
    testAllMatch(parser, "true");
    testAllMatch(parser, "true | false ");
    testAllMatch(parser, "true & $foo == 1");
    testAllMatch(parser, "$hour>0 & $hour<5");
  }

}
