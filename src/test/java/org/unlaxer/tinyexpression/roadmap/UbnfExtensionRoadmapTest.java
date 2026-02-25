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
  public void testBackreferenceSemanticsMethodInvocationWithDeclarationSucceeds() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapBackrefDeclaredMethod"),
        new Source("call provide()\nobject provide(){\n 'ok'\n}"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    Object result = calculator.apply(CalculationContext.newConcurrentContext());
    assertEquals("ok", result);
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

  @Test
  public void testScopeTreeLexicalSemanticsMethodParameterShadowsGlobalObjectVariable() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapScopeTreeObject"),
        new Source(
            "var $payload description='payload';\n"
                + "call identity('local')\n"
                + "object identity($payload as object){\n"
                + " $payload\n"
                + "}"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    CalculationContext context = CalculationContext.newConcurrentContext();
    context.setObject("payload", "global");
    Object result = calculator.apply(context);
    assertEquals("local", result);
  }

  @Test
  public void testScopeTreeLexicalSemanticsMethodParameterShadowsGlobalStringVariable() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapScopeTreeString"),
        new Source(
            "var $name as string description='name';\n"
                + "call identity('local')\n"
                + "string identity($name as string){\n"
                + " $name\n"
                + "}"),
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("name", "global");
    Object result = calculator.apply(context);
    assertEquals("local", result);
  }

}
