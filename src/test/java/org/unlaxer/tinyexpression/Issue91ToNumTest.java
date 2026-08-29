package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Regression for tinyexpression#91:
 * <ul>
 *   <li>{@code toNum('abc', 'xyz')} with a non-{@link Number} default value
 *       threw {@link ClassCastException} via {@code (Number) eval(...)}.</li>
 *   <li>{@code toNum(...)} returned {@code Double} regardless of the
 *       configured {@code resultType}, because {@code castToNumberType}
 *       was not applied.</li>
 * </ul>
 */
public class Issue91ToNumTest {

  private final ClassLoader cl = Thread.currentThread().getContextClassLoader();

  private Object eval(String formula, SpecifiedExpressionTypes types) {
    Calculator c = CalculatorCreatorRegistry.astEvaluatorCreator()
        .create(new Source(formula), "Issue91_" + Math.abs(formula.hashCode()), types, cl);
    return c.apply(CalculationContext.newConcurrentContext());
  }

  // --- Problem 1: non-Number default value must not throw ClassCastException ---

  @Test
  public void toNumWithStringDefaultReturnsZeroInsteadOfThrowing() {
    Object r = eval("toNum('abc', 'xyz')",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    // non-Number default falls back to 0.0, cast through numberType -> Float
    assertEquals(Float.valueOf(0.0f), r);
  }

  @Test
  public void toNumWithBooleanDefaultReturnsZeroInsteadOfThrowing() {
    Object r = eval("toNum('abc', true)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    assertEquals(Float.valueOf(0.0f), r);
  }

  @Test
  public void toNumWithNumberDefaultReturnsDefaultValue() {
    Object r = eval("toNum('abc', 42)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    assertEquals(Float.valueOf(42.0f), r);
  }

  // --- Problem 2: resultType must be respected via castToNumberType ---

  @Test
  public void toNumReturnsFloatWhenResultTypeIsFloat() {
    Object r = eval("toNum('3.14', 0)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    assertEquals(Float.class, r.getClass());
    assertEquals(3.14f, ((Number) r).floatValue(), 0.001f);
  }

  @Test
  public void toNumReturnsDoubleWhenResultTypeIsDouble() {
    // numberType (2nd arg) drives castToNumberType; set it to _double so the
    // returned value is a Double rather than the default Float.
    Object r = eval("toNum('3.14', 0)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._double));
    assertEquals(Double.class, r.getClass());
    assertEquals(3.14, ((Number) r).doubleValue(), 0.001);
  }

  @Test
  public void toNumDefaultValueCastThroughNumberType() {
    Object r = eval("toNum('abc', 42)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    assertEquals(Float.class, r.getClass());
    assertEquals(42.0f, ((Number) r).floatValue(), 0.001f);
  }
}
