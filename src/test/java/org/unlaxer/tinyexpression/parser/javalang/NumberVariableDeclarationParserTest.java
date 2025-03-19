package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableDeclarationParser;

public class NumberVariableDeclarationParserTest extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.detail);

    var variableDeclarationParser = new NumberVariableDeclarationParser();

    testAllMatch(variableDeclarationParser, "variable $age as number set if not exists 18 description='年齢をセットします。';");
    testAllMatch(variableDeclarationParser, "variable $age as number set 18 description='年齢をセットします。';");
    testAllMatch(variableDeclarationParser, "variable $age as number description='年齢をセットします。';");
    testAllMatch(variableDeclarationParser, "variable $age number description='年齢をセットします。';");
    testAllMatch(variableDeclarationParser, "var $withdrawalStatsLastTime as long description='入力値';");



    testUnMatch(variableDeclarationParser, "variable $isMale as boolean set if not exists 1==1 description='maleかどうかをセットします。';");

    //description is not exists
    testUnMatch(variableDeclarationParser, "variable $age as number set 18;");

  }
}