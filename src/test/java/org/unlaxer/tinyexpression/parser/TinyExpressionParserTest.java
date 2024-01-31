package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.util.SimpleBuilder;

public class TinyExpressionParserTest extends ParserTestBase{

  @Test
  public void testMaximum() {
    
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
      .line("  external returning as number : Fee#calculate($age,$taxRate)")
      .line("}");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
    
  }
  
  @Test
  public void testMinimum() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("if($sex=='woman'){")
      .line("  0 //女性無料")
      .line("}else{")
      .line("  external returning as number : Fee#calculate($age,$taxRate)")
      .line("}");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }

  @Test
  public void testMinimum2() {
    
    setLevel(OutputLevel.detail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("1+(8/4)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testImport() {
    
    setLevel(OutputLevel.detail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .n()
      .line("  external returning number : calculate($age as number ,1000,$taxRate as number)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testVariableDeclarations() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .line("var $age as number set if not exists 18 description='年齢';")
      .n()
      .line("external number calculate($age/* as number */,1000,$taxRate as number)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testWithoutVariableDeclarations() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
//      .line("var $age as number set if not exists 18 description='年齢';")
      .n()
      .line("external number calculate($age/* as number */,1000,$taxRate as number)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    // $ageに型はついていないが、型なしの場合はnumberになるようにしている。互換性のため。
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testBooleanVariableDeclarations() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .line("var $free as boolean set if not exists true description='タダかどうか';")
      .n()
      .line("external number calculate($age as number ,if($free){0}else{1000},$taxRate as number)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testStringVariableDeclarations() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .line("var $name as string set if not exists '渡辺' description='苗字を設定します';")
      .n()
      .line("external number calculate($age as number ,if($name=='渡辺'){0}else{1000},$taxRate as number)");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testSimpleExpression() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .n()
//      .line("float main(){")
      .line(" match{")
      .line("  $age<18  -> 1000,")
      .line("  $age>=60 -> 1000,")
      .line("  default  -> 1800")
      .line(" }")
//      .line("}")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testMethod() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("import org.unlaxer.tinyexpression.Fee#calculate as calculate;")
      .n()
      .line("float main(){")
      .line(" match{")
      .line("  $age<18  -> 1000,")
      .line("  $age>=60 -> 1000,")
      .line("  default  -> 1800")
      .line(" }")
      .line("}")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testNoParameterSideEffect() {
    
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("if($sex=='woman'){")
      .line("  0 //女性無料")
      .line("}else{")
      .line("  external returning as number : Fee#calculate()")
      .line("}");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testCollections() {
    setLevel(OutputLevel.mostDetail);
    
    TinyExpressionParser tinyExpressionParser = new TinyExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("var $test as Tuple<Number,String,Boolean>[69,'scott tiger',true] description='scott tiger information' ;")
;
    //      .line("var $test2 as Tuple<Number,List<String>,Tuple<Number,Boolean>>[69,['scott tiger','marco '],true] description='scott tiger information' ;")
//      .line("var $test3 as List<Tuple<Number,List<String>,Tuple<Number,Boolean>>>[[69,['scott tiger','marco '],true] ,[69,['scott tiger','marco '],true]]  ;");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(tinyExpressionParser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
    
  }

}
