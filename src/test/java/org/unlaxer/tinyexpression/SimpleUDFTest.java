package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class SimpleUDFTest {

  @Test
  public void testSimple() {
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("sex", "male");

    // create UDF
    String udf = "if($sex=='male'){500}else{1000}";

    // create calculator
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("Test"), // name for identifier
        udf, // user define function
        new SpecifiedExpressionTypes(
            ExpressionTypes._float, // result type of this udf returning
            ExpressionTypes._float // default number type. eg. float,double,integer,short...
        ),
        Thread.currentThread().getContextClassLoader());// classloader for generated class from udf
    
    
    {
      // test with male
      float apply = (float)calculator.apply(context);
      assertEquals(500.0f, apply , 0.1);
    }

    
    {
      // test with female
      context.set("sex", "female");
      float apply = (float)calculator.apply(context);
      assertEquals(1000.0f, apply , 0.1);
    }
  }

}
