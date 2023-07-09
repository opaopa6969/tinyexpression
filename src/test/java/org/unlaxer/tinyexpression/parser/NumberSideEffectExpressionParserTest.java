package org.unlaxer.tinyexpression.parser;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleBuilder;

public class NumberSideEffectExpressionParserTest extends ParserTestBase{

  @Test
  public void testSideEffect() {
    
    setLevel(OutputLevel.mostDetail);

    var parser = new NumberSideEffectExpressionParser();
    
    SimpleBuilder simpleBuilder = new SimpleBuilder();

    simpleBuilder
      .line("external number calculate($age as number ,if($name=='渡辺'){0}else{1000},$taxRate as number)");
    String formula = simpleBuilder.toString();
    System.out.println(formula);
    
    testAllMatch(parser, formula);
  }

}
