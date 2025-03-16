package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class NakedVariableDeclarationParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    var variableDeclarationParser = new NakedVariableDeclarationParser();
    
    testUnMatch(variableDeclarationParser, "variable $age as number set if not exists 18 description='年齢をセットします。';");
    testUnMatch(variableDeclarationParser, "variable $age as number set 18 description='年齢をセットします。';");
    testUnMatch(variableDeclarationParser, "variable $age as number description='年齢をセットします。';");
    testUnMatch(variableDeclarationParser, "variable $age number description='年齢をセットします。';");

    testUnMatch(variableDeclarationParser, "variable $isMale as boolean set if not exists 1==1 description='maleかどうかをセットします。';");

    //description is not exists
    testUnMatch(variableDeclarationParser, "variable $age as number set 18;");
    testUnMatch(variableDeclarationParser, "variable $age set 18;");

  }
}