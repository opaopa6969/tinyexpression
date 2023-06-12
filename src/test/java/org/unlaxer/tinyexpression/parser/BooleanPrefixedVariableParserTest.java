package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.listener.OutputLevel;

public class BooleanPrefixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new BooleanPrefixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "(boolean) $hoge ");
    
    Token rootToken = testAllMatch.parsed.getRootToken();
    String variableName = BooleanPrefixedVariableParser.getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
