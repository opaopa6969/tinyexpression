package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.parser.NakedVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.number.NumberVariableDeclarationParser;
import org.unlaxer.tinyexpression.parser.string.StringVariableDeclarationParser;

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
  
  public class NumberVariableDeclarationParserTest extends ParserTestBase{

    @Test
    public void test() {
      setLevel(OutputLevel.detail);
      
      var variableDeclarationParser = new NumberVariableDeclarationParser();
      
      testAllMatch(variableDeclarationParser, "variable $age as number set if not exists 18 description='年齢をセットします。';");
      testAllMatch(variableDeclarationParser, "variable $age as number set 18 description='年齢をセットします。';");
      testAllMatch(variableDeclarationParser, "variable $age as number description='年齢をセットします。';");
      testAllMatch(variableDeclarationParser, "variable $age number description='年齢をセットします。';");

      testUnMatch(variableDeclarationParser, "variable $isMale as boolean set if not exists 1==1 description='maleかどうかをセットします。';");

      //description is not exists
      testUnMatch(variableDeclarationParser, "variable $age as number set 18;");

    }
  }
  
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

}
