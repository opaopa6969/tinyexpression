package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorStringGeneratedPathTest {

  @Test
  public void testStringLiteralUsesGeneratedAstPath() {
    String formula = "'hello'";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstStringGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals("hello", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }
}
