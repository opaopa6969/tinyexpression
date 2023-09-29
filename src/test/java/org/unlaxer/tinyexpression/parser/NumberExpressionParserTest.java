package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;

public class NumberExpressionParserTest extends ParserTestBase{

  @Test
  public void test() {
    
    setLevel(OutputLevel.detail);

    NumberExpressionParser parser = new NumberExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("if($sex=='woman'){")
      .line("  0 ")
      .line("}else{")
      .line("  external returning as number : Fee#calculate($age,$taxRate)")
      .line("}");
    
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    testAllMatch(parser, formula);
  }
  
  @Test
  public void testSideEffect() {
    
    setLevel(OutputLevel.mostDetail);

    NumberExpressionParser parser = new NumberExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("external number calculate($age as number ,if($name=='渡辺'){0}else{1000},$taxRate as number)");
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    testAllMatch(parser, formula);
  }
}
