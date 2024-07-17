package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.elementary.WordParser;

public class CodeStartParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    CodeStartParser codeStartParser = new CodeStartParser();
    testAllMatch(codeStartParser, "```java:a.b.Co");
    testAllMatch(new Chain(codeStartParser, new WordParser("A")),"```java:a.b.Co\nA");
  }

}
