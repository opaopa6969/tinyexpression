package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class StringSetterParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.mostDetail);
    
    StringSetterParser setterParser = new StringSetterParser();
    testAllMatch(setterParser, "set $hoge");
    testUnMatch(setterParser, "set true");
    testUnMatch(setterParser, "set 1");
    testUnMatch(setterParser, "set 10/5");
    testUnMatch(setterParser, "set if not exists 10/5");
    testAllMatch(setterParser, "set $hoge as String");
    testUnMatch(setterParser, "set $hoge as number");
  }

}
