package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Tests for {@link JavaCodeBlockPolicy} — opt-in disable of arbitrary Java code execution.
 *
 * @since 1.4.11
 */
public class JavaCodeBlockPolicyTest {

  @Before
  public void setUp() {
    JavaCodeBlockPolicy.reset(); // ensure default state before each test
  }

  @After
  public void tearDown() {
    JavaCodeBlockPolicy.reset(); // restore default after each test
  }

  // =========================================================================
  // Default state
  // =========================================================================

  @Test
  public void testDefaultIsEnabled() {
    assertTrue("Java code block execution should be enabled by default",
        JavaCodeBlockPolicy.isEnabled());
  }

  // =========================================================================
  // setEnabled / isEnabled round-trip
  // =========================================================================

  @Test
  public void testSetEnabledFalse() {
    JavaCodeBlockPolicy.setEnabled(false);
    assertFalse("After setEnabled(false), isEnabled() should return false",
        JavaCodeBlockPolicy.isEnabled());
  }

  @Test
  public void testSetEnabledTrue() {
    JavaCodeBlockPolicy.setEnabled(false);
    JavaCodeBlockPolicy.setEnabled(true);
    assertTrue("After setEnabled(true), isEnabled() should return true",
        JavaCodeBlockPolicy.isEnabled());
  }

  @Test
  public void testResetRestoresDefault() {
    JavaCodeBlockPolicy.setEnabled(false);
    JavaCodeBlockPolicy.reset();
    assertTrue("reset() should restore the enabled=true default",
        JavaCodeBlockPolicy.isEnabled());
  }

  // =========================================================================
  // Functional: normal formula evaluation is unaffected when code blocks disabled
  // =========================================================================

  @Test
  public void testNormalArithmeticUnaffectedWhenCodeBlockDisabled() {
    JavaCodeBlockPolicy.setEnabled(false);

    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    String formula = "1+2";

    Calculator calc = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "PolicyTest_arith", types, cl);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    Object result = calc.apply(ctx);
    assertNotNull("result should not be null", result);
    assertEquals("1+2 should equal 3.0 even when code blocks disabled",
        3f, ((Number) result).floatValue(), 0.001f);
  }

  // =========================================================================
  // Functional: code block section is skipped (no compilation) when disabled
  // =========================================================================

  @Test
  public void testCodeBlockSkippedWhenDisabled() {
    // Formula with an embedded Java code block (the block defines a class).
    // When JavaCodeBlockPolicy is enabled (default), the class gets compiled.
    // When disabled, createJavaFromCodedBlock returns empty list — no exception thrown.
    String formula = "```java:org.unlaxer.test.Policy1\n"
        + "package org.unlaxer.test;\n"
        + "public class Policy1 { public Policy1() {} }\n"
        + "```\n"
        + "1+1";

    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    // Enabled (default): should compile without exception
    Calculator calcEnabled = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "PolicyTest_enabled", types, cl);
    assertNotNull("calculator should be created when policy is enabled", calcEnabled);

    // Disabled: code block compilation skipped, formula evaluation still works
    JavaCodeBlockPolicy.setEnabled(false);
    Calculator calcDisabled = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "PolicyTest_disabled", types, cl);
    assertNotNull("calculator should be created even when policy is disabled", calcDisabled);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    Object result = calcDisabled.apply(ctx);
    assertNotNull("result should not be null when policy is disabled", result);
    assertEquals("1+1 should equal 2.0 even with code blocks disabled",
        2f, ((Number) result).floatValue(), 0.001f);
  }
}
