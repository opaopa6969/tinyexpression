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
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ThreeExecutionBackendParityTest {

  @Test
  public void testLegacyAstAndDslJavaCodeBackendsMatchOnSupportedCorpus() {
    List<Case> cases = List.of(
        new Case("1+(8/4)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if   (true){1}else{2}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==1->3,default->5}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==1->'A',default->'B'}", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("call provide()\nobject provide(){\n'ok'\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("call identity('payload')\nobject identity($payload as object){\n$payload\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists 3 description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null));

    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Calculator legacy = CalculatorCreatorRegistry.forBackend(ExecutionBackend.JAVA_CODE).create(
          new Source(testCase.formula), "ThreeWayLegacy_" + i, testCase.types, classLoader);
      Calculator ast = CalculatorCreatorRegistry.forBackend(ExecutionBackend.AST_EVALUATOR).create(
          new Source(testCase.formula), "ThreeWayAst_" + i, testCase.types, classLoader);
      Calculator dslJava = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE).create(
          new Source(testCase.formula), "ThreeWayDslJava_" + i, testCase.types, classLoader);

      CalculationContext legacyContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      CalculationContext dslJavaContext = CalculationContext.newConcurrentContext();
      if (testCase.preparation != null) {
        testCase.preparation.accept(legacyContext);
        testCase.preparation.accept(astContext);
        testCase.preparation.accept(dslJavaContext);
      }

      Object legacyValue = legacy.apply(legacyContext);
      Object astValue = ast.apply(astContext);
      Object dslJavaValue = dslJava.apply(dslJavaContext);

      assertEquivalent(testCase.formula, legacyValue, astValue);
      assertEquivalent(testCase.formula, legacyValue, dslJavaValue);
      assertNotEquals("formula=" + testCase.formula, "javacode-fallback",
          ast.getObject("_astEvaluatorRuntime", String.class));
      assertEquals("dsl-javacode", dslJava.getObject("_tinyExecutionMode", String.class));
    }
  }

  private void assertEquivalent(String formula, Object expected, Object actual) {
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
