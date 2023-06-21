package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class TinyExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import java.lang.String as String;")
      .line("import org.unlaxer.tinyexpression.parser.AdmissionFee as Fee;")
      .n()
      .line("variable $sex as string set if not exists 'man' description='性別をセットします。(man/woman)。';")
      .line("variable $age as number set if not exists 18 description='年齢をセットします。';")
      .line("variable $taxRate as number set 10.0 description='税率をセットします。';//税率が決め打ちだったので変数化")
      .n()
      .line("@document(author='opa' , description='年齢を入力して金額を返します' ,since='2023/06/01')")
      .line("@Version1.0 初期バージョン")
      .line("@Version1.1 消費税率変更対応")
      .n()
      .line("/*")
      .line(" * サンプルです")
      .line(" * import文,Variable文,AnnotationとlineAnnotationとブロックコメントとCPPコメントを利用しています")
      .line("　*/")
      .n()
      .line("if($sex=='woman'){")
      .line("  0 //女性無料")
      .line("}else{")
      .line("  external returning as number default 0 : Fee#calculate($age,$taxRate)")
      .line("}");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
    
  }

}
