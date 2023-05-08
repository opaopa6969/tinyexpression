package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class DescriptionParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    DescriptionParser descriptionParser = new DescriptionParser();
    testAllMatch(descriptionParser, "description='abc'");
    testUnMatch(descriptionParser, "description=abc");
  }

}
