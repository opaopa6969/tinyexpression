package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class ReturningNumberParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    ReturningNumberParser returningNumberParser = new ReturningNumberParser();
    
    testAllMatch(returningNumberParser, "returning as number");
    
    
  }

}
