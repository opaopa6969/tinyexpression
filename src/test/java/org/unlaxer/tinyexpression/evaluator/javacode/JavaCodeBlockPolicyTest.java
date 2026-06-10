package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
    JavaCodeBlockPolicy.reset(); // ensure default state (disabled) before each test
  }

  @After
  public void tearDown() {
    JavaCodeBlockPolicy.reset(); // restore default (disabled) after each test
  }

  // =========================================================================
  // Default state
  // =========================================================================

  @Test
  public void testDefaultIsDisabled() {
    assertFalse("Java code block execution should be disabled by default",
        JavaCodeBlockPolicy.isEnabled());
  }

  // =========================================================================
  // setEnabled / isEnabled round-trip
  // =========================================================================

  @Test
  public void testSetEnabledFalse() {
    JavaCodeBlockPolicy.setEnabled(true);
    JavaCodeBlockPolicy.setEnabled(false);
    assertFalse("After setEnabled(false), isEnabled() should return false",
        JavaCodeBlockPolicy.isEnabled());
  }

  @Test
  public void testSetEnabledTrue() {
    JavaCodeBlockPolicy.setEnabled(true);
    assertTrue("After setEnabled(true), isEnabled() should return true",
        JavaCodeBlockPolicy.isEnabled());
  }

  @Test
  public void testResetRestoresDefault() {
    JavaCodeBlockPolicy.setEnabled(true);
    JavaCodeBlockPolicy.reset();
    assertFalse("reset() should restore the disabled=false default",
        JavaCodeBlockPolicy.isEnabled());
  }

  // =========================================================================
  // Functional: normal formula evaluation is unaffected when code blocks disabled
  // =========================================================================

  @Test
  public void testNormalArithmeticUnaffectedWhenCodeBlockDisabled() {
    // default is disabled — no need to call setEnabled(false)

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
  // Functional: formula with code block is rejected (with diagnostic) when disabled
  // =========================================================================

  @Test
  public void testCodeBlockRejectedWhenDisabled() {
    // Formula with an embedded Java code block.
    // When JavaCodeBlockPolicy is disabled (default), createJavaFromCodedBlock throws
    // a CompileError with a diagnostic message — callers are not silently surprised.
    String formula = "```java:org.unlaxer.test.Policy1\n"
        + "package org.unlaxer.test;\n"
        + "public class Policy1 { public Policy1() {} }\n"
        + "```\n"
        + "1+1";

    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    // Disabled (default): should throw CompileError with a diagnostic message
    try {
      CalculatorCreatorRegistry.javaCodeCreator()
          .create(new Source(formula), "PolicyTest_disabled", types, cl);
      fail("Expected CompileError when Java code block policy is disabled");
    } catch (org.unlaxer.compiler.CompileError e) {
      assertTrue("Error message should mention JavaCodeBlockPolicy",
          e.getMessage() != null && e.getMessage().contains("JavaCodeBlockPolicy"));
    }
  }

  @Test
  public void testCodeBlockCompiledWhenEnabled() {
    // Explicit opt-in: should compile without exception
    JavaCodeBlockPolicy.setEnabled(true);

    String formula = "```java:org.unlaxer.test.Policy2\n"
        + "package org.unlaxer.test;\n"
        + "public class Policy2 { public Policy2() {} }\n"
        + "```\n"
        + "1+1";

    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Calculator calcEnabled = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "PolicyTest_enabled", types, cl);
    assertNotNull("calculator should be created when policy is enabled", calcEnabled);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    Object result = calcEnabled.apply(ctx);
    assertNotNull("result should not be null when policy is enabled", result);
    assertEquals("1+1 should equal 2.0 when code blocks enabled",
        2f, ((Number) result).floatValue(), 0.001f);
  }
}
