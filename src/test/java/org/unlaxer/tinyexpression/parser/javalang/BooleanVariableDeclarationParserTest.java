package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;

public class BooleanVariableDeclarationParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);
    
    var variableDeclarationParser = new BooleanVariableDeclarationParser();
    
    testAllMatch(variableDeclarationParser, "variable $isMale as boolean set if not exists 1==1 description='maleかどうかをセットします。';");
    testAllMatch(variableDeclarationParser, "variable $isMale as boolean set true description='maleかどうかをセットします。';");
    testAllMatch(variableDeclarationParser, "variable $isMale as boolean description='maleかどうかをセットします。';");
    testAllMatch(variableDeclarationParser, "variable $isMale boolean description='maleかどうかをセットします。';");

    testUnMatch(variableDeclarationParser, "variable $age number description='性別をセットします。(man/woman)。';//コメント");

    //description is not exists
    testUnMatch(variableDeclarationParser, "variable $isMale as boolean set true;");

  }
}