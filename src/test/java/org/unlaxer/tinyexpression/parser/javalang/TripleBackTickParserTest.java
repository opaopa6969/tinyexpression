package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class TripleBackTickParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    TripleBackTickParser parser = new TripleBackTickParser();
    
    testAllMatch(parser, "```");
    testUnMatch(parser, "`'`");
    testUnMatch(parser, "``");
  }

}
