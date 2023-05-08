package org.unlaxer.tinyexpression.parser;

import static org.junit.Assert.*;

import org.junit.Test;
import org.unlaxer.ParserTestBase;

public class SideEffectExpressionParameterParserTest extends ParserTestBase{

  @Test
  public void test() {
    var parser = new SideEffectExpressionParameterParser();
    testAllMatch(parser, "$foo as float , $bar as String , $hoge as boolean");
  }

}
