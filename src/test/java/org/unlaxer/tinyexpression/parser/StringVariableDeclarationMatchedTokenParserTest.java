package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;

public class StringVariableDeclarationMatchedTokenParserTest extends ParserTestBase{

  @Test
  public void test() {

    
    var parser = new StringVariableDeclarationMatchedTokenParser();
    
    testSucceededOnly(parser, "var $foo as string description='';", true , DoAssert.yes);
  }

}
