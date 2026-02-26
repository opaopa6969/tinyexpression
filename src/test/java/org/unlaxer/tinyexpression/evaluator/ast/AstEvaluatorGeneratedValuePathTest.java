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

  @Test
  public void testObjectVariableUsesGeneratedAstPath() {
    String formula = "$payload";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstObjectVariableGeneratedPath", types, classLoader);

    CalculationContext context = CalculationContext.newConcurrentContext();
    context.setObject("payload", "ctx-object");
    Object value = ast.apply(context);

    assertEquals("ctx-object", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testObjectDeclarationSetterUsesGeneratedAstPath() {
    String formula =
        "var $payload set if not exists 'fallback' description='payload';\n"
            + "$payload";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstObjectDeclarationGeneratedPath", types, classLoader);

    CalculationContext context = CalculationContext.newConcurrentContext();
    Object value = ast.apply(context);

    assertEquals("fallback", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
    assertEquals("fallback", context.getObject("payload", Object.class).orElse(null));
  }

  @Test
  public void testTypedDeclarationSettersUseGeneratedAstPath() {
    assertGeneratedDeclarationFormula(
        "var $price as number set if not exists 3 description='price';\n$price+2",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        5f);
    assertGeneratedDeclarationFormula(
        "var $name as string set if not exists 'neo' description='name';\n$name",
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float),
        "neo");
    assertGeneratedDeclarationFormula(
        "var $enabled as boolean set if not exists true description='enabled';\n$enabled",
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float),
        true);
  }

  @Test
  public void testStringMatchUsesGeneratedAstPath() {
    String formula = "match{true->'niku',default->'sushi'}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstStringMatchGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals("niku", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testMethodInvocationWithDeclarationUsesGeneratedAstPath() {
    String formula = "call provide()\nobject provide(){\n'ok'\n}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstMethodInvocationGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals("ok", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testMethodInvocationWithArgumentsUsesGeneratedAstPath() {
    String formula = "call identity('local')\nobject identity($payload as object){\n$payload\n}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstMethodInvocationWithArgsGeneratedPath", types, classLoader);

    CalculationContext context = CalculationContext.newConcurrentContext();
    context.setObject("payload", "global");
    Object value = ast.apply(context);

    assertEquals("local", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testNumberIfExpressionUsesGeneratedAstPath() {
    String formula = "if   (true){1}else{2}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstNumberIfGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals(1f, ((Number) value).floatValue(), 0.0001f);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test
  public void testStringIfExpressionUsesGeneratedAstPath() {
    String formula = "if(true){'ok'}else{'ng'}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstStringIfGeneratedPath", types, classLoader);

    Object value = ast.apply(CalculationContext.newConcurrentContext());

    assertEquals("ok", value);
    assertEquals("generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }

  private void assertGeneratedDeclarationFormula(String formula, SpecifiedExpressionTypes types, Object expected) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "AstDeclarationGeneratedPath", types, classLoader);
    CalculationContext context = CalculationContext.newConcurrentContext();

    Object value = ast.apply(context);

    if (expected instanceof Number number) {
      assertEquals(number.floatValue(), ((Number) value).floatValue(), 0.0001f);
    } else {
      assertEquals(expected, value);
    }
    assertEquals("formula=" + formula, "generated-ast", ast.getObject("_astEvaluatorRuntime", String.class));
  }
}
