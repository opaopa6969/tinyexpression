package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.listener.OutputLevel;

public class BooleanSuffixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var booleanSuffixedVariableParser = new BooleanSuffixedVariableParser();
    TestResult testAllMatch = testAllMatch(booleanSuffixedVariableParser, "$hoge as boolean");
    
    Token rootToken = testAllMatch.parsed.getRootToken();
    String variableName = BooleanSuffixedVariableParser.getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
