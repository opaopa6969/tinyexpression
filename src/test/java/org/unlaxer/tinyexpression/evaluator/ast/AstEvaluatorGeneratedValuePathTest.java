package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorGeneratedValuePathTest {

  @Test
  public void testBooleanVariableUsesGeneratedAstPath() {
    String formula = "true";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstBooleanGeneratedPath", types, classLoader);

    CalculationContext context = CalculationContext.newConcurrentContext();
    Object value = ast.apply(context);

    assertEquals(true, value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testObjectStringLiteralUsesGeneratedAstPath() {
    String formula = "'payload'";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstObjectGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals("payload", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }
}
