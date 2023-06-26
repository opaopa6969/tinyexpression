package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class ReturningStringParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    ReturningStringParser returningStringParser = new ReturningStringParser();
    
    testAllMatch(returningStringParser, "returning as string// default \"niku\"");
    testAllMatch(returningStringParser, "returning string// default \"niku\"");
    testAllMatch(returningStringParser, "as string// default \"niku\"");
    testAllMatch(returningStringParser, "string");
    testAllMatch(returningStringParser, "String");
  }

}
