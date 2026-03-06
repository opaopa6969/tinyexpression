package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.TestResult;
import org.unlaxer.Token;
import org.unlaxer.TokenPrinter;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class MethodsParserTest extends ParserTestBase{

  @Test
  public void testMethod() {
    
    setLevel(OutputLevel.mostDetail);
    
    var parser = new MethodsParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
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
    
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testEmpty() {
    
    setLevel(OutputLevel.mostDetail);
    
    var parser = new MethodsParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .append("")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }
  
  @Test
  public void testMethods() {
    
    setLevel(OutputLevel.mostDetail);
    
    var parser = new MethodsParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("float main(){")
      .line(" match{")
      .line("  $age<18  -> 500,")
      .line("  $age>=60 -> 700,")
      .line("  default  -> call feeBySex($sex)")
      .line(" }")
      .line("}")
      .n()
      .line("float feeBySex($sex as string){")
      .line(" match{")
      .line("  $sex=='woman' -> 1000,")
      .line("  default  -> 1800")
      .line(" }")
      .line("}")
      ;
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }

  @Test
  public void testObjectMethod() {

    setLevel(OutputLevel.mostDetail);

    var parser = new MethodsParser();

    SimpleBuilder simpleBuilder = new SimpleBuilder();
    simpleBuilder
      .line("object provide(){")
      .line(" 'ok'")
      .line("}");

    String formula = simpleBuilder.toString();
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }

  @Test
  public void testObjectMethodWithObjectParameter() {
    setLevel(OutputLevel.mostDetail);
    var parser = new MethodsParser();

    SimpleBuilder simpleBuilder = new SimpleBuilder();
    simpleBuilder
      .line("object identity($payload as object){")
      .line(" $payload")
      .line("}");

    String formula = simpleBuilder.toString();
    TestResult testAllMatch = testAllMatch(parser, formula);
    Token rootToken = testAllMatch.parsed.getRootToken();
    String string = TokenPrinter.get(rootToken);
    System.out.println(string);
  }

}
