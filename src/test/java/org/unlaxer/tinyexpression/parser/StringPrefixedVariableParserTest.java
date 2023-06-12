package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.listener.OutputLevel;

public class StringPrefixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new StringPrefixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "(string) $hoge ");
    
    Token rootToken = testAllMatch.parsed.getRootToken();
    String variableName = StringPrefixedVariableParser.getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
