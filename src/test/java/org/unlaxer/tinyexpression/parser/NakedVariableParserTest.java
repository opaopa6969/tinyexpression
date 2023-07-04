package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NakedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    NakedVariableParser nakedVariableParser = new NakedVariableParser();
    testAllMatch(nakedVariableParser, "$test");
    testPartialMatch(nakedVariableParser, "$test as number", "$test");

  
    ExclusiveNakedVariableParser exclusiveNakedVariableParser = new ExclusiveNakedVariableParser();
    testAllMatch(exclusiveNakedVariableParser, "$test");
    testUnMatch(exclusiveNakedVariableParser, "$test as number");
}

}
