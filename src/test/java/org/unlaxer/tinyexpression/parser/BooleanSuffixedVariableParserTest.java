package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.TypedToken;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.booltype.BooleanSuffixedVariableParser;

public class BooleanSuffixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var booleanSuffixedVariableParser = new BooleanSuffixedVariableParser();
    TestResult testAllMatch = testAllMatch(booleanSuffixedVariableParser, "$hoge as boolean");
    
    TypedToken<VariableParser> rootToken = testAllMatch.parsed.getRootToken().typed(VariableParser.class);
    String variableName = BooleanSuffixedVariableParser.get().getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
