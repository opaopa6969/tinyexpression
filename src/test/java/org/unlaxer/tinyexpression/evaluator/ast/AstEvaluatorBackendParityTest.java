package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorBackendParityTest {

  @Test
  public void testNumberExpressionsMatchJavaCodeBackend() {
    List<String> formulas = List.of(
        "1+(8/4)",
        "3*4-5",
        "(10-2)*(7-3)");

    for (int i = 0; i < formulas.size(); i++) {
      String formula = formulas.get(i);
      SpecifiedExpressionTypes types =
          new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
          new Source(formula), "ParityJavaCode_" + i, types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(formula), "ParityAst_" + i, types, classLoader);

      CalculationContext context = CalculationContext.newConcurrentContext();
      float javaValue = ((Number) javaCode.apply(context)).floatValue();
      float astValue = ((Number) ast.apply(context)).floatValue();

      assertEquals(javaValue, astValue, 0.0001f);
      String runtime = ast.getObject("_astEvaluatorRuntime", String.class);
      assertNotEquals("javacode-fallback", runtime);
    }
  }

  @Test
  public void testGeneratedAstPrefersTopLevelExpressionOverDeclarationSetter() {
    String formula =
        "var $price as number set if not exists 3 description='price';\n"
            + "$price+2";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
        new Source(formula), "ParityJavaCode_withDeclaration", types, classLoader);
    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "ParityAst_withDeclaration", types, classLoader);

    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("price", 3f);

    float javaValue = ((Number) javaCode.apply(context)).floatValue();
    float astValue = ((Number) ast.apply(context)).floatValue();

    assertEquals(javaValue, astValue, 0.0001f);
    assertEquals(5f, astValue, 0.0001f);
  }
}
