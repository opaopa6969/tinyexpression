package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.TypedToken;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;

public class StringPrefixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new StringPrefixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "(string) $hoge ");
    
    TypedToken<VariableParser> rootToken = testAllMatch.parsed.getRootToken().typed(VariableParser.class);
    String variableName = Parser.get(StringPrefixedVariableParser.class).getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
