package org.unlaxer.tinyexpression.parser.javalang;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class AnnotationsParserTest extends ParserTestBase{

  @Test
  public void test() {
   setLevel(OutputLevel.detail);
    
   AnnotationsParser parser = new AnnotationsParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
//      .line("@Version1.0 初期バージョン");
      .line("@document(author='opa' , description='年齢を入力して金額を返します' , since='2023/06/01')")
      .line("@document(author='opaopa' , description='niku' , since='2023/06/01')")
      .line("@Version1.1 消費税率変更対応")
      .n();
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    testAllMatch(parser, formula);
  }

}
