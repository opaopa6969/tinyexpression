package org.unlaxer.tinyexpression.evaluator.p4;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.CalculatorImplTest;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * {@link P4AstEvaluatorCalculatorTest}'s whole CalculatorImplTest suite again, with the P4 parser engine switched to
 * {@code classic} through the JVM-wide escape hatch {@code -Dtinyexpression.p4.engine=classic}
 * (issue #183). The default-engine run is {@link P4AstEvaluatorCalculatorTest} itself. Every calculator is built
 * through {@link CalculatorCreatorRegistry} and must carry the {@code classic} engine marker.
 */
public class P4AstEvaluatorCalculatorClassicEngineTest extends CalculatorImplTest {

  // classic backtracks exponentially on the deep fraud-alert formulas (#19, #20) and, on a loaded
  // CI runner, hits the 10 s parse / 5 s probe deadlines. This suite checks results, not speed.
  private static final String[] PROPERTIES = {
      P4ParserEngine.SYSTEM_PROPERTY, "classic",
      "tinyexpression.p4.parse.timeout.millis", "0",
      "tinyexpression.p4.probe.timeout.millis", "0"};
  private static final String[] previous = new String[PROPERTIES.length / 2];

  @BeforeClass
  public static void selectClassicEngine() {
    for (int i = 0; i < previous.length; i++) {
      previous[i] = System.setProperty(PROPERTIES[2 * i], PROPERTIES[2 * i + 1]);
    }
  }

  @AfterClass
  public static void restoreEngine() {
    for (int i = 0; i < previous.length; i++) {
      if (previous[i] == null) {
        System.clearProperty(PROPERTIES[2 * i]);
      } else {
        System.setProperty(PROPERTIES[2 * i], previous[i]);
      }
    }
  }

  @Override
  public Calculator preConstructedCalculator(Source formula) {
    String className = "P4AstClassicTest_CalculatorClass" + Math.abs(formula.source().hashCode());
    Calculator calculator = CalculatorCreatorRegistry.forBackend(ExecutionBackend.P4_AST_EVALUATOR).create(
        formula,
        className,
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    assertEquals("classic", calculator.getObject(P4ParserEngine.CALCULATOR_MARKER, String.class));
    return calculator;
  }
}
