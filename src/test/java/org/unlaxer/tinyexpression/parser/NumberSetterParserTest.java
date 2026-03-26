package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NumberSetterParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    NumberSetterParser setterParser = new NumberSetterParser();
    testAllMatch(setterParser, "set $hoge");
    testUnMatch(setterParser, "set true");
    testAllMatch(setterParser, "set 1");
    testAllMatch(setterParser, "set 10/5");
    testAllMatch(setterParser, "set if not exists 10/5");
    testUnMatch(setterParser, "set $hoge as String");
  }

}
