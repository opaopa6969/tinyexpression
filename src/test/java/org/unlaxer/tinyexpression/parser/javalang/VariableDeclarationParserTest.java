package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class VariableDeclarationParserTest extends ParserTestBase{

  @Test
  public void testString() {
    setLevel(OutputLevel.detail);
    
    StringVariableDeclarationParser variableDeclarationParser = new StringVariableDeclarationParser();
    
    testAllMatch(variableDeclarationParser, "variable $sex as string set if not exists 'man' description='性別をセットします。(man/woman)。';");
    testAllMatch(variableDeclarationParser, "variable $age as number set if not exists 18 description='年齢をセットします。';");
    //WhiteSpaceDelimitedだとpartial
//    testPartialMatch(variableDeclarationParser, "variable $taxRate as number set 10.0 description='税率をセットします。';//税率が決め打ちだったので変数化" , "variable $taxRate as number set 10.0 description='税率をセットします。';");
    testAllMatch(variableDeclarationParser, "variable $taxRate as number set 10.0 description='税率をセットします。';//税率が決め打ちだったので変数化");
    testAllMatch(variableDeclarationParser, "var $taxRate as number set 10.0 description='税率をセットします。';//税率が決め打ちだったので変数化");
    testAllMatch(variableDeclarationParser, "var $age as number set if not exists 18 description='年齢をセットします。';");
    testAllMatch(variableDeclarationParser, "var $age number set 18 description='年齢をセットします。';");
    
    //description is not exists
    testUnMatch(variableDeclarationParser, "var $age as number set if not exists 18 ;");

    
  }

}
