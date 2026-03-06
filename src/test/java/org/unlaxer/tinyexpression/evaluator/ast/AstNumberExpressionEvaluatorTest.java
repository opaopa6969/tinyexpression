package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstNumberExpressionEvaluatorTest {

  @Test
  public void testTryEvaluateNumberExpression() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    CalculationContext context = CalculationContext.newContext();

    Optional<Object> result = AstNumberExpressionEvaluator.tryEvaluate("1+(8/4)", types, context);

    assertTrue(result.isPresent());
    assertEquals(3.0f, ((Number) result.get()).floatValue(), 0.00001f);
  }

  @Test
  public void testTryEvaluateReturnsEmptyForUnsupportedExpression() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    CalculationContext context = CalculationContext.newContext();

    Optional<Object> result =
        AstNumberExpressionEvaluator.tryEvaluate("if(1<2){1}else{2}", types, context);

    assertFalse(result.isPresent());
  }
}
