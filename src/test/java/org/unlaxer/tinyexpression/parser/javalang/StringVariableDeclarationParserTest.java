package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.stringtype.StringVariableDeclarationParser;

public class StringVariableDeclarationParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.mostDetail);
    
    var variableDeclarationParser = new StringVariableDeclarationParser();
    
    testAllMatch(variableDeclarationParser, "variable $sex as string set if not exists 'man' description='性別をセットします。(man/woman)。';");
    testAllMatch(variableDeclarationParser, "variable $sex as string set 'man' description='性別をセットします。(man/woman)。';");
    testAllMatch(variableDeclarationParser, "variable $sex as string description='性別をセットします。(man/woman)。';");
    testAllMatch(variableDeclarationParser, "variable $sex string description='性別をセットします。(man/woman)。';//コメント");

    testUnMatch(variableDeclarationParser, "variable $age number description='性別をセットします。(man/woman)。';//コメント");

    //description is not exists
    testUnMatch(variableDeclarationParser, "var $sex string set if not exists 'woman';");

  }
}
