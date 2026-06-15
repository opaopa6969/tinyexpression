package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Known, reproduced P4 evaluation bugs surfaced once the P4 primary path was
 * revived (see docs/findings-2026-06-15-unlaxer-3.0.4-and-p4.md). Each test states
 * the CORRECT expected value and is @Ignore'd so the suite stays green; removing the
 * @Ignore turns each into a regression check for when the underlying generator /
 * cross-check bug is fixed.
 *
 * All four reproduce on the AST_EVALUATOR (P4-typed interpreter) backend.
 */
public class KnownP4BugsTest {

  private final ClassLoader cl = Thread.currentThread().getContextClassLoader();

  /**
   * P4-BUG-1 (mapper / generator): a parenthesised boolean group used as an operand
   * of &amp; / | / ^ is mis-mapped. The generated TinyExpressionP4Mapper captures the
   * literal "(" token as BooleanFactorExpr.value (the `'true'`/`'false'` alternatives
   * compile to a catch-all WordParser lookup that matches the "(" token before the
   * parenthesised-expression alternative is tried). Result: the inner OR/AND/XOR is
   * dropped. Fix belongs in unlaxer-dsl MapperGenerator.
   */
  @Ignore("P4-BUG-1 paren-boolean operand mis-mapped (unlaxer-dsl mapper)")
  @Test public void parenthesisedBooleanOperand() {
    assertBool("(true | false) & (false | true)", true);
    assertBool("true & (false | true)", true);
    assertBool("(true & false) | (true & true)", true);
  }

  /**
   * P4-BUG-2 (mapper / generator): a ternary whose then-branch is itself a ternary is
   * mis-mapped; `(true ? (false ? 1 : 2) : 3)` evaluates to 1 instead of 2.
   */
  @Ignore("P4-BUG-2 nested ternary mis-mapped (unlaxer-dsl mapper)")
  @Test public void nestedTernary() {
    assertNum("(true ? (false ? 1 : 2) : 3)", 2);
  }

  /**
   * P4-BUG-3 (cross-check + legacy): min/max with more than two arguments. The
   * P4-typed interpreter computes the correct result (min(3,5,1,9)=1), but
   * AstEvaluatorCalculator cross-checks number results against the legacy token-AST
   * evaluator, whose variadic min/max is broken (returns 3), and on mismatch it
   * trusts the (wrong) legacy value. Two defects: legacy variadic min/max is broken,
   * and the cross-check prefers legacy over the correct P4 result.
   */
  @Ignore("P4-BUG-3 variadic min/max overridden by buggy cross-check")
  @Test public void variadicMinMax() {
    assertNum("min(3,5,1,9)", 1);
    assertNum("max(3,5,1,9)", 9);
  }

  /**
   * P4-BUG-4 (cross-check): boolean operator precedence with comparison operands
   * inside if(). P4-typed correctly evaluates `1>0 | 0>1 & 1>2` as 1>0 | (0>1 & 1>2)
   * = true (AND binds tighter than OR), but the number-result cross-check overrides it
   * with the legacy flat-left-associative value (false). Same root cause as P4-BUG-3.
   */
  @Ignore("P4-BUG-4 boolean precedence in if overridden by cross-check")
  @Test public void crossCheckOverridesCorrectP4Precedence() {
    assertNum("if(1>0 | 0>1 & 1>2){1}else{0}", 1);
  }

  /**
   * P4-BUG-5 (cross-check / legacy): arithmetic that combines function-call terms,
   * e.g. {@code abs(-3)+pow(2,3)} = 11, returns 3 — the legacy token-AST evaluator drops
   * the trailing {@code +pow(2,3)} term and the cross-check trusts it over the correct
   * P4 result. Same root cause as P4-BUG-3/4.
   */
  @Ignore("P4-BUG-5 function-term arithmetic dropped by cross-check")
  @Test public void crossCheckDropsFunctionArithmetic() {
    assertNum("abs(-3)+pow(2,3)", 11);
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
