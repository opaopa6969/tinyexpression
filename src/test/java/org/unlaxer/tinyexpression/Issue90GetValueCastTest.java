package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.Test;

/**
 * Regression for tinyexpression#90:
 * {@code set(String, Number)} stores arbitrary {@link Number} subtypes into
 * {@code valueByName}, but {@code getValue(String)} cast the stored value to
 * {@link Float} unconditionally, causing {@link ClassCastException} for
 * {@code Double}/{@code Integer}/{@code BigInteger} values.
 */
public class Issue90GetValueCastTest {

  @Test
  public void getValueAfterSetDoubleDoesNotThrow() {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("x", Double.valueOf(3.14));
    Optional<Float> v = ctx.getValue("x");
    assertEquals(3.14f, v.get(), 0.001f);
  }

  @Test
  public void getValueAfterSetIntegerDoesNotThrow() {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("n", Integer.valueOf(42));
    Optional<Float> v = ctx.getValue("n");
    assertEquals(42f, v.get(), 0.001f);
  }

  @Test
  public void getValueAfterSetBigIntegerDoesNotThrow() {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("b", BigInteger.valueOf(7));
    Optional<Float> v = ctx.getValue("b");
    assertEquals(7f, v.get(), 0.001f);
  }

  @Test
  public void getValueAfterSetFloatPreservesExactValue() {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("f", 1.25f);
    Optional<Float> v = ctx.getValue("f");
    assertEquals(1.25f, v.get(), 0.0f);
  }

  @Test
  public void getValueForAbsentReturnsEmpty() {
    CalculationContext ctx = CalculationContext.newConcurrentContext();
    Optional<Float> v = ctx.getValue("absent");
    assertEquals(Optional.empty(), v);
  }
}
