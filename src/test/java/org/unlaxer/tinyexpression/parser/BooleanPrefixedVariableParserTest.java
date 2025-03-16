package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.TypedToken;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.booltype.BooleanPrefixedVariableParser;

public class BooleanPrefixedVariableParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    var parser = new BooleanPrefixedVariableParser();
    TestResult testAllMatch = testAllMatch(parser, "(boolean) $hoge ");
    
    TypedToken<VariableParser> rootToken = testAllMatch.parsed.getRootToken().typed(VariableParser.class);
    String variableName = Parser.get(BooleanPrefixedVariableParser.class).getVariableName(rootToken);
    
    System.out.println(variableName);
    
    assertEquals("hoge", variableName);
  }

}
