package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class SetterParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    SetterParser setterParser = new SetterParser();
    testAllMatch(setterParser, "set $hoge");
    testAllMatch(setterParser, "set true");
    testAllMatch(setterParser, "set 1");
    testAllMatch(setterParser, "set 10/5");
    testAllMatch(setterParser, "set if not exists 10/5");
    testAllMatch(setterParser, "set $hoge as String");
  }

}
