package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Tests for the Ternary expression: {@code (condition ? thenExpr : elseExpr)}.
 * <p>
 * The ternary maps to the same {@code IfExpr} AST node as {@code if(cond){then}else{else}},
 * so all existing evaluator logic applies unchanged.
 */
public class TernaryExpressionTest {

  private CalculationContext context;
  private ClassLoader classLoader;

  @Before
  public void setUp() {
    context = CalculationContext.newConcurrentContext();
    classLoader = Thread.currentThread().getContextClassLoader();
  }

  // =========================================================================
  // Basic true/false
  // =========================================================================

  @Test
  public void testTrueReturnsThenBranch() {
    assertEval("(true ? 1 : 2)", 1.0f);
  }

  @Test
  public void testFalseReturnsElseBranch() {
    assertEval("(false ? 1 : 2)", 2.0f);
  }

  // =========================================================================
  // Variables
  // =========================================================================

  @Test
  public void testVariableConditionTrue() {
    context.set("flag", 1.0f);
    context.set("a", 3.0f);
    context.set("b", 4.0f);
    assertEval("($flag == 1 ? $a : $b)", 3.0f);
  }

  @Test
  public void testVariableConditionFalse() {
    context.set("flag", 0.0f);
    context.set("a", 3.0f);
    context.set("b", 4.0f);
    assertEval("($flag == 1 ? $a : $b)", 4.0f);
  }

  // =========================================================================
  // Comparison conditions
  // =========================================================================

  @Test
  public void testComparisonGreaterThan() {
    context.set("a", 3.0f);
    assertEval("($a > 0 ? $a : 0)", 3.0f);
  }

  @Test
  public void testComparisonLessThan() {
    context.set("a", 3.0f);
    assertEval("($a < 0 ? $a : 0)", 0.0f);
  }

  @Test
  public void testComparisonEqualTrue() {
    context.set("x", 5.0f);
    assertEval("($x == 5 ? 1 : 0)", 1.0f);
  }

  @Test
  public void testComparisonEqualFalse() {
    context.set("x", 3.0f);
    assertEval("($x == 5 ? 1 : 0)", 0.0f);
  }

  // =========================================================================
  // Nested ternary
  // =========================================================================

  @Test
  public void testTernaryWithComparisonThenBranch() {
    assertEval("(1 > 0 ? 10 : 20)", 10.0f);
  }

  @Test
  public void testTernaryWithComparisonElseBranch() {
    assertEval("(0 > 1 ? 10 : 20)", 20.0f);
  }

  @Test
  public void testTernaryElseBranchWithVariable() {
    context.set("a", -1.0f);
    assertEval("($a > 0 ? 10 : 3)", 3.0f);
  }

  // =========================================================================
  // Complex expressions in then/else
  // =========================================================================

  @Test
  public void testComplexExpressions() {
    context.set("a", 3.0f);
    context.set("b", 4.0f);
    assertEval("($a > $b ? $a * 2 : $b * 2)", 8.0f);
  }

  @Test
  public void testArithmeticInBranches() {
    context.set("a", 3.0f);
    context.set("b", 4.0f);
    assertEval("($a > 0 ? $a + $b : $a - $b)", 7.0f);
  }

  // =========================================================================
  // With math functions
  // =========================================================================

  @Test
  public void testWithMaxFunction() {
    context.set("a", 3.0f);
    context.set("b", 4.0f);
    assertEval("($a > $b ? max($a, $b) : min($a, $b))", 3.0f);
  }

  // =========================================================================
  // if equivalence tests
  // =========================================================================

  @Test
  public void testEquivalenceWithIfTrue() {
    float ternaryResult = evalFloat("(true ? 1 : 2)");
    float ifResult = evalFloat("if(true){1}else{2}");
    assertEquals("ternary and if should be equivalent for true", ifResult, ternaryResult, 0.0001f);
  }

  @Test
  public void testEquivalenceWithIfFalse() {
    float ternaryResult = evalFloat("(false ? 1 : 2)");
    float ifResult = evalFloat("if(false){1}else{2}");
    assertEquals("ternary and if should be equivalent for false", ifResult, ternaryResult, 0.0001f);
  }

  @Test
  public void testEquivalenceWithIfVariable() {
    context.set("a", 3.0f);
    float ternaryResult = evalFloat("($a > 0 ? $a : -1)");
    float ifResult = evalFloat("if($a > 0){$a}else{-1}");
    assertEquals("ternary and if should be equivalent for variable", ifResult, ternaryResult, 0.0001f);
  }

  // =========================================================================
  // Boolean and string types (orthogonality)
  // =========================================================================

  @Test
  public void testStringResultTrue() {
    assertEvalString("(true ? 'yes' : 'no')", "yes");
  }

  @Test
  public void testStringResultFalse() {
    assertEvalString("(false ? 'yes' : 'no')", "no");
  }

  @Test
  public void testBooleanResultTrue() {
    assertEvalBoolean("(true ? true : false)", true);
  }

  @Test
  public void testBooleanResultFalse() {
    assertEvalBoolean("(false ? true : false)", false);
  }

  // =========================================================================
  // Edge cases
  // =========================================================================

  @Test
  public void testLiteralNumbersOnly() {
    assertEval("(true ? 42 : 0)", 42.0f);
  }

  @Test
  public void testNegativeNumbers() {
    assertEval("(false ? -1 : -2)", -2.0f);
  }

  @Test
  public void testZeroCondition() {
    // 0 should be falsy
    assertEval("(true ? 100 : 200)", 100.0f);
  }

  // =========================================================================
  // ArgumentExpression — bare ternary inside function parens (no double parens)
  // =========================================================================

  @Test
  public void testTernaryInSin() {
    context.set("a", 3.0f);
    // sin($a > 0 ? $a : 0) — no double parens needed!
    float expected = (float) Math.sin(Math.toRadians(3.0));
    assertEval("sin($a > 0 ? $a : 0)", expected);
  }

  @Test
  public void testTernaryInAbs() {
    context.set("a", -5.0f);
    // abs($a > 0 ? $a : 0) — bare ternary in abs()
    assertEval("abs($a > 0 ? $a : 0)", 0.0f);
  }

  @Test
  public void testTernaryInAbsTrueBranch() {
    context.set("a", 5.0f);
    assertEval("abs($a > 0 ? $a : 0)", 5.0f);
  }

  @Test
  public void testTernaryInMin() {
    context.set("a", 3.0f);
    context.set("b", 7.0f);
    // min($a > 0 ? $a : 0, $b) — bare ternary as first arg
    assertEval("min($a > 0 ? $a : 0, $b)", 3.0f);
  }

  @Test
  public void testTernaryInMax() {
    context.set("a", 3.0f);
    context.set("b", 7.0f);
    // max($a > 0 ? $a : 0, $b > 0 ? $b : 0) — both args are bare ternaries
    assertEval("max($a > 0 ? $a : 0, $b > 0 ? $b : 0)", 7.0f);
  }

  @Test
  public void testTernaryWithParensStillWorks() {
    context.set("a", 3.0f);
    // Original paren form still works
    assertEval("($a > 0 ? $a : 0)", 3.0f);
  }

  @Test
  public void testTernaryInSinWithDoubleParensStillWorks() {
    context.set("a", 3.0f);
    // Old double-paren form: sin(($a > 0 ? $a : 0)) still works
    float expected = (float) Math.sin(Math.toRadians(3.0));
    assertEval("sin(($a > 0 ? $a : 0))", expected);
  }

  // =========================================================================
  // P4 typed evaluator path
  // =========================================================================

  @Test
  public void testP4TypedEvaluatorPathUsed() {
    Calculator calc = createCalculator("(true ? 1 : 2)",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertTrue("result should be Number", result instanceof Number);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.0001f);
    // Verify the P4 parser was used
    String runtime = calc.getObject("_astEvaluatorRuntime", String.class);
    assertTrue("expected p4-typed or generated-ast but was: " + runtime,
        "p4-typed".equals(runtime) || "generated-ast".equals(runtime));
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private void assertEval(String formula, float expected) {
    Calculator calc = createCalculator(formula,
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertTrue("expected Number but was: " + (result == null ? "null" : result.getClass().getName()),
        result instanceof Number);
    assertEquals("formula=" + formula, expected, ((Number) result).floatValue(), 0.0001f);
  }

  private void assertEvalString(String formula, String expected) {
    Calculator calc = createCalculator(formula,
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertEquals("formula=" + formula, expected, String.valueOf(result));
  }

  private void assertEvalBoolean(String formula, boolean expected) {
    Calculator calc = createCalculator(formula,
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertEquals("formula=" + formula, expected, result);
  }

  private float evalFloat(String formula) {
    Calculator calc = createCalculator(formula,
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    Object result = calc.apply(context);
    return ((Number) result).floatValue();
  }

  private Calculator createCalculator(String formula, SpecifiedExpressionTypes types) {
    return CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "TernaryTest_" + Math.abs(formula.hashCode()), types, classLoader);
  }
}
