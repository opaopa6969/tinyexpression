package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Objects;

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

  @Test
  public void testAstEvaluatorRejectsWrongPreferredIfRootFallback() {
    List<String> formulas = List.of(
        "if(max(-1,-2)==-1){1}else{0}",
        "if(false|false|false|(true&true)){1}else{0}",
        "if(true!=false){1}else{0}",
        "if('cnjpuszn'[4:6].in('en','ca','us')){1}else{0}");
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    for (int i = 0; i < formulas.size(); i++) {
      String formula = formulas.get(i);
      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
          new Source(formula), "ParityJavaCode_ifRoot_" + i, types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(formula), "ParityAst_ifRoot_" + i, types, classLoader);

      CalculationContext context = CalculationContext.newConcurrentContext();
      float javaValue = ((Number) javaCode.apply(context)).floatValue();
      float astValue = ((Number) ast.apply(context)).floatValue();

      assertEquals("formula=" + formula, javaValue, astValue, 0.0001f);
      String fallbackReason = ast.getObject("_p4FallbackReason", String.class);
      assertTrue("formula=" + formula + " should not accept a shallow non-if AST root",
          fallbackReason == null || !fallbackReason.startsWith("cross-check mismatch"));
    }
  }

  @Test
  public void testContextfulIfExpressionsMatchJavaCodeBackend() {
    List<Case> cases = List.of(
        new Case(
            "if($country.in('ca','jp','us')){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
            context -> context.set("country", "jp")),
        new Case(
            "if(inDayTimeRange(MONDAY,10,FRIDAY,23)==true){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
            context -> {
              context.set("nowDayOfWeek", 3f);
              context.set("nowHour", 12f);
            }));

    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
          new Source(testCase.formula), "ParityJavaCode_contextIf_" + i, testCase.types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(testCase.formula), "ParityAst_contextIf_" + i, testCase.types, classLoader);

      CalculationContext javaContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      testCase.preparation.accept(javaContext);
      testCase.preparation.accept(astContext);

      float javaValue = ((Number) javaCode.apply(javaContext)).floatValue();
      float astValue = ((Number) ast.apply(astContext)).floatValue();

      assertEquals("formula=" + testCase.formula, javaValue, astValue, 0.0001f);
      assertNotEquals("formula=" + testCase.formula, "javacode-fallback",
          ast.getObject("_astEvaluatorRuntime", String.class));
    }
  }

  @Test
  public void testValueExpressionsMatchJavaCodeBackendOnComplexNonNumberCases() {
    List<Case> cases = List.of(
        new Case(
            "match{1==1->'A',default->'B'}",
            new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float),
            null),
        new Case(
            "match{1==0->false,default->true}",
            new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float),
            null),
        new Case(
            "isPresent($name)",
            new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float),
            context -> context.set("name", "hello")),
        new Case(
            "call provide()\nobject provide(){\n'ok'\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
            null));

    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
          new Source(testCase.formula), "ParityJavaCode_nonNumber_" + i, testCase.types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(testCase.formula), "ParityAst_nonNumber_" + i, testCase.types, classLoader);

      CalculationContext javaContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      if (testCase.preparation != null) {
        testCase.preparation.accept(javaContext);
        testCase.preparation.accept(astContext);
      }

      Object javaValue = javaCode.apply(javaContext);
      Object astValue = ast.apply(astContext);

      assertEquals("formula=" + testCase.formula, String.valueOf(javaValue), String.valueOf(astValue));
      String runtime = ast.getObject("_astEvaluatorRuntime", String.class);
      assertNotEquals("formula=" + testCase.formula, "javacode-fallback", runtime);
    }
  }

  private record Case(String formula, SpecifiedExpressionTypes types,
      java.util.function.Consumer<CalculationContext> preparation) {
    private Case {
      Objects.requireNonNull(formula, "formula");
      Objects.requireNonNull(types, "types");
    }
  }
}
