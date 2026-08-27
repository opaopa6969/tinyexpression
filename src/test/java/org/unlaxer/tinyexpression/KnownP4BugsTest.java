package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Regression tests for P4 interpreter correctness bugs found while adding comprehensive
 * coverage. The original issues are closed, so every reproduction runs in the normal suite.
 * See docs/findings-2026-06-15-unlaxer-3.0.4-and-p4.md.
 */
public class KnownP4BugsTest {

  private final ClassLoader cl = Thread.currentThread().getContextClassLoader();

  /**
   * tinyexpression#25: a standalone top-level {@code not(...)} formula evaluated with a
   * {@code _boolean} result type always returns false on the P4 interpreter (the outer
   * NotExpr is dropped during root mapping). {@code not(...)} inside {@code if(...)} works,
   * and the javacode backend is correct.
   */
  @Test public void standaloneNotReturnsFalse() {
    assertBool("not(false)", true);
    assertBool("not(not(true))", true);
    assertBool("not(1>2)", true);
  }

  /**
   * Cross-check defect: the P4-typed interpreter computes min/max of &gt;2 args correctly
   * (min(3,5,1,9)=1) but AstEvaluatorCalculator cross-checks number results against the
   * legacy token-AST evaluator (whose variadic min/max is broken, returns 3) and trusts
   * the legacy value on mismatch. Removing the cross-check is tracked in tinyexpression#21.
   */
  @Test public void variadicMinMax() {
    assertNum("min(3,5,1,9)", 1);
    assertNum("max(3,5,1,9)", 9);
  }

  /**
   * Cross-check defect: P4-typed evaluates `1>0 | 0>1 & 1>2` correctly as 1>0|(0>1&1>2)=true,
   * but the number-result cross-check overrides it with the legacy flat-precedence value.
   * Tracked in tinyexpression#21.
   */
  @Test public void booleanPrecedenceInIf() {
    assertNum("if(1>0 | 0>1 & 1>2){1}else{0}", 1);
  }

  /**
   * AST-type defect (unlaxer-parser#43): arithmetic combining math-function terms,
   * e.g. abs(-3)+pow(2,3)=11, is mis-evaluated because the generated BinaryExpr record
   * types its operands as BinaryExpr (not the common AST interface), so a MathFunction
   * operand cannot be represented.
   */
  @Test public void functionTermArithmetic() {
    assertNum("abs(-3)+pow(2,3)", 11);
  }

  /** Regression guard: nested ternary is handled correctly on current master. */
  @Test public void nestedTernary() {
    assertNum("(true ? (false ? 1 : 2) : 3)", 2);
  }

  // helpers
  private void assertNum(String formula, float expected) {
    Calculator c = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "Bug_" + Math.abs(formula.hashCode()),
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), cl);
    assertEquals(formula, expected, ((Number) c.apply(CalculationContext.newConcurrentContext())).floatValue(), 0.01f);
  }

  private void assertBool(String formula, boolean expected) {
    Calculator c = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "BugB_" + Math.abs(formula.hashCode()),
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), cl);
    assertEquals(formula, expected, c.apply(CalculationContext.newConcurrentContext()));
  }
}
