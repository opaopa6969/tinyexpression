package org.unlaxer.tinyexpression.evaluator.javacode;

import org.junit.Test;
import org.unlaxer.ParserTestBase;
import org.unlaxer.listener.OutputLevel;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.parser.FormulaParser;

public class JavaCodeNumberCalculatorV2Test extends ParserTestBase{

  @Test
  public void test() {
    setLevel(OutputLevel.mostDetail);
    CalculationContext context = CalculationContext.newConcurrentContext();
    
    
    FormulaParser formulaParser = new FormulaParser();
    testAllMatch(formulaParser, "1+1");
//    StringSource source = new StringSource("1+1");
//    ParseContext parseContext = new ParseContext(source);
//    Parsed parse = formulaParser.parse(parseContext);
    
    
//    JavaCodeNumberCalculatorV2 javaCodeNumberCalculatorV2 = new JavaCodeNumberCalculatorV2(Name.of("test"), "1+1");
//    
//    Float apply = javaCodeNumberCalculatorV2.apply(context);
//    System.out.println(apply);
  }

}
