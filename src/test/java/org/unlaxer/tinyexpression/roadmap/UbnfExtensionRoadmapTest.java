package org.unlaxer.tinyexpression.roadmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.parser.ParseException;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class UbnfExtensionRoadmapTest {

  @Test
  public void testInterleaveSemanticsJavaStyleWithCommentAndWhitespace() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapInterleave"),
        new Source("var $age as number set if not exists 18 description='age'; // trailing comment\n $age+2"),
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    Object result = calculator.apply(CalculationContext.newConcurrentContext());
    assertEquals(20.0f, ((Number) result).floatValue(), 0.0001f);
  }

  @Test
  public void testBackreferenceSemanticsMethodInvocationRequiresDeclaration() {
    ParseException exception = assertThrows(
        ParseException.class,
        () -> new JavaCodeCalculatorV3(
            Name.of("RoadmapBackrefMissingMethod"),
            new Source("call missing()"),
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
            Thread.currentThread().getContextClassLoader()));

    Throwable cause = exception.getCause();
    while (cause != null && cause.getCause() != null) {
      cause = cause.getCause();
    }
    String message = cause == null ? exception.getMessage() : cause.getMessage();
    assertTrue(message.contains("missing is not declared"));
  }

  @Test
  public void testScopeTreeLexicalSemanticsMethodParameterShadowsGlobalNumberVariable() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapScopeTree"),
        new Source(
            "var $amount as number set if not exists 100 description='amount';\n"
                + "call identity(1)\n"
                + "float identity($amount as number){\n"
                + " $amount\n"
                + "}"),
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    Object result = calculator.apply(CalculationContext.newConcurrentContext());
    assertEquals(1.0f, ((Number) result).floatValue(), 0.0001f);
  }

}
