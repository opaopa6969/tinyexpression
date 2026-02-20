package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.TypedToken;
import org.unlaxer.listener.OutputLevel;

public class NumberSuffixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new NumberSuffixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "$hoge as number");
    
    TypedToken<VariableParser> rootToken = testAllMatch.parsed.getRootToken().typed(VariableParser.class);
    String variableName = parser.getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
