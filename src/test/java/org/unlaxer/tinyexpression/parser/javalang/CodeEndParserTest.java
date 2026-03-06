package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.combinator.Chain;

public class CodeEndParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    CodeEndParser codeEndParser = new CodeEndParser();
    
    testAllMatch(codeEndParser, "```");
    testAllMatch(codeEndParser, "```\n");
    testAllMatch(new Chain(new org.unlaxer.parser.elementary.WordParser("\n") ,codeEndParser), "\n```\n");
  }

}
