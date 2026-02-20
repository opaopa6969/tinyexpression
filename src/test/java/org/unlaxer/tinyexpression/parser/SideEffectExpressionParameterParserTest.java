package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;

public class SideEffectExpressionParameterParserTest extends ParserTestBase{

  @Test
  public void test() {
    var parser = new ArgumentsParser();
    testAllMatch(parser, "$foo as float , $bar as String , $hoge as boolean");
    testAllMatch(parser, "(float)$foo , (string)$bar , (boolean)$hoge");
    testAllMatch(parser, "(float)$foo , (string)$bar , (boolean)$hoge //@niku niku");

    TestResult testAllMatch = testAllMatch(parser, "(float)$foo /*前置*/, (string)$bar , (boolean)$hoge//前置");
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    TokenPrinter.output(rootToken, System.out);    
  }

}
