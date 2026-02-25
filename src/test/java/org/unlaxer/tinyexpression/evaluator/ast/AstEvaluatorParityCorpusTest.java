package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorParityCorpusTest {

  @Test
  public void testCuratedParityCorpusAvoidsJavaCodeFallback() {
    List<Case> cases = List.of(
        new Case("1+(8/4)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if   (true){1}else{2}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==1->'A',default->'B'}", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("match{1==0->false,default->true}", new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), null),
        new Case("'payload'", new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("$payload", new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
            context -> context.setObject("payload", "ctx-object")),
        new Case("call provide()\nobject provide(){\n'ok'\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("var $name as string set if not exists 'neo' description='name';\n$name",
            new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists 3 description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null));

    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator().create(
          new Source(testCase.formula), "ParityCorpusJava_" + i, testCase.types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(testCase.formula), "ParityCorpusAst_" + i, testCase.types, classLoader);

      CalculationContext javaContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      if (testCase.preparation != null) {
        testCase.preparation.accept(javaContext);
        testCase.preparation.accept(astContext);
      }

      Object javaValue = javaCode.apply(javaContext);
      Object astValue = ast.apply(astContext);
      assertValueEquivalent(testCase.formula, javaValue, astValue);
      assertNotEquals("formula=" + testCase.formula, "javacode-fallback",
          ast.getObject("_astEvaluatorRuntime", String.class));
    }
  }

  private void assertValueEquivalent(String formula, Object expected, Object actual) {
    if (expected instanceof Number left && actual instanceof Number right) {
      BigDecimal l = new BigDecimal(String.valueOf(left));
      BigDecimal r = new BigDecimal(String.valueOf(right));
      assertEquals("formula=" + formula, 0, l.compareTo(r));
      return;
    }
    assertEquals("formula=" + formula, String.valueOf(expected), String.valueOf(actual));
  }

  private record Case(String formula, SpecifiedExpressionTypes types, Consumer<CalculationContext> preparation) {
    private Case {
      Objects.requireNonNull(formula, "formula");
      Objects.requireNonNull(types, "types");
    }
  }
}
