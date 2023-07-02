package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NakedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    NakedVariableParser nakedVariableParser = new NakedVariableParser();
    testAllMatch(nakedVariableParser, "$test");
//  testPartialMatch(nakedVariableParser, "$test as String" , "$test");
    testUnMatch(nakedVariableParser, "$test as number");
  }

}
