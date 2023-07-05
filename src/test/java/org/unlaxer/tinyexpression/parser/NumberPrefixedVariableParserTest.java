package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.listener.OutputLevel;

public class NumberPrefixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new NumberPrefixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "(number) $hoge ");
    
    Token rootToken = testAllMatch.parsed.getRootToken();
    String variableName = parser.getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
