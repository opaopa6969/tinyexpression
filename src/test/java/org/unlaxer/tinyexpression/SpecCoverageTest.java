package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * End-to-end spec coverage tests for areas not covered by existing test suites.
 *
 * Uses AST_EVALUATOR backend as the primary test backend because it supports
 * all documented features via its 4-stage fallback chain (P4TypedAstEvaluator →
 * GeneratedP4NumberAstEvaluator → AstTokenTreeEvaluator → JAVA_CODE fallback).
 * P4_AST_EVALUATOR is used for features strictly requiring the P4 UBNF parser.
 *
 * Covers SPEC.md sections: 2.2, 2.4, 2.6, 2.7.3, 2.8.1 (toNum), 2.8.2 (.in),
 *   2.9 (floor/ceil/abs/sqrt/log/exp/random/round/pow), 2.11, 2.15, 5.3.
 */
public class SpecCoverageTest {

  private CalculationContext context;
  private ClassLoader classLoader;

  @Before
  public void setUp() {
    context = CalculationContext.newConcurrentContext();
    classLoader = Thread.currentThread().getContextClassLoader();
  }

  // =========================================================================
  // Spec 2.9 — Math functions: floor, ceil, abs, sqrt, log, exp, random
  // AST_EVALUATOR supports all documented math functions via its fallback chain.
  // =========================================================================

  /** Spec 2.9: floor(n) — truncates toward negative infinity */
  @Test
  public void mathFunction_floor_positiveDecimal() {
    assertEval("floor(3.9)", 3.0f);
  }

  /** Spec 2.9: floor(n) — floor of negative decimal */
  @Test
  public void mathFunction_floor_negativeDecimal() {
    assertEval("floor(-1.1)", -2.0f);
  }

  /** Spec 2.9: ceil(n) — rounds toward positive infinity */
  @Test
  public void mathFunction_ceil_positiveDecimal() {
    assertEval("ceil(3.1)", 4.0f);
  }

  /** Spec 2.9: ceil(n) — ceil of negative decimal */
  @Test
  public void mathFunction_ceil_negativeDecimal() {
    assertEval("ceil(-3.9)", -3.0f);
  }

  /** Spec 2.9: round(n) — standard rounding up */
  @Test
  public void mathFunction_round_roundsUp() {
    assertEval("round(2.6)", 3.0f);
  }

  /** Spec 2.9: round(n) — rounds down */
  @Test
  public void mathFunction_round_roundsDown() {
    assertEval("round(2.4)", 2.0f);
  }

  /** Spec 2.9: pow(x,y) — exponentiation: 2^10=1024 */
  @Test
  public void mathFunction_pow_largeExponent() {
    assertEval("pow(2,10)", 1024.0f);
  }

  /** Spec 2.9: pow(x,y) — 3^2=9 */
  @Test
  public void mathFunction_pow_squaring() {
    assertEval("pow(3,2)", 9.0f);
  }

  /** Spec 2.9: sqrt(n) — square root: sqrt(9)=3 */
  @Test
  public void mathFunction_sqrt() {
    assertEval("sqrt(9)", 3.0f);
  }

  /** Spec 2.9: abs(n) — absolute value of negative */
  @Test
  public void mathFunction_abs_negative() {
    assertEval("abs(-7)", 7.0f);
  }

  /** Spec 2.9: abs(n) — absolute value of positive stays positive */
  @Test
  public void mathFunction_abs_positive() {
    assertEval("abs(5)", 5.0f);
  }

  /** Spec 2.9: log(n) — natural logarithm: log(1)=0 */
  @Test
  public void mathFunction_log_one() {
    assertEval("log(1)", 0.0f);
  }

  /** Spec 2.9: exp(n) — natural exponential: exp(0)=1 */
  @Test
  public void mathFunction_exp_zero() {
    assertEval("exp(0)", 1.0f);
  }

  /** Spec 2.9: random() — should return value in [0,1) */
  @Test
  public void mathFunction_random_inRange() {
    Calculator calc = createAstCalc("random()",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertNotNull("random() should return a value", result);
    float v = ((Number) result).floatValue();
    assertTrue("random() should be >= 0", v >= 0.0f);
    assertTrue("random() should be < 1", v < 1.0f);
  }

  // =========================================================================
  // Spec 2.6 — Operator precedence
  // =========================================================================

  /** Spec 2.6: * before + : 2+3*4 = 14, not 20 */
  @Test
  public void operatorPrecedence_multiplyBeforeAdd() {
    assertEval("2+3*4", 14.0f);
  }

  /** Spec 2.6: * before - : 10-2*3 = 4, not 24 */
  @Test
  public void operatorPrecedence_multiplyBeforeSubtract() {
    assertEval("10-2*3", 4.0f);
  }

  /** Spec 2.6: parens override precedence: (2+3)*4 = 20 */
  @Test
  public void operatorPrecedence_parensOverride() {
    assertEval("(2+3)*4", 20.0f);
  }

  /** Spec 2.6: comparison lower than arithmetic: 2+3 > 4 → true */
  @Test
  public void operatorPrecedence_comparisonLowerThanArithmetic() {
    assertEval("if(2+3>4){1}else{0}", 1.0f);
  }

  /** Spec 2.6: / before + : 1+8/4 = 3 */
  @Test
  public void operatorPrecedence_divideBeforeAdd() {
    assertEval("1+8/4", 3.0f);
  }

  /** Spec 2.6: nested parens with multiplication: (10-2)*(7-3)=32 */
  @Test
  public void operatorPrecedence_nestedParenMultiply() {
    assertEval("(10-2)*(7-3)", 32.0f);
  }

  // =========================================================================
  // Spec 2.8.1 — Built-in string function: toNum
  // =========================================================================

  /** Spec 2.8.1: toNum(s, default) — returns default for non-numeric string */
  @Test
  public void stringFunction_toNum_withDefault_invalidString() {
    context.set("numStr", "not-a-number");
    assertEval("toNum($numStr, 99)", 99.0f);
  }

  /** Spec 2.8.1: toNum(s, default) — returns parsed value when valid */
  @Test
  public void stringFunction_toNum_withDefault_validString() {
    context.set("numStr", "7");
    assertEval("toNum($numStr, 0)", 7.0f);
  }

  // =========================================================================
  // Spec 2.8.2 — Dot-form string method: .in()
  // =========================================================================

  /** Spec 2.8.2: .in(str, str, ...) — true if variable matches any value */
  @Test
  public void dotMethod_in_matches() {
    context.set("code", "JP");
    assertEval("if($code.in('JP','US','CA')){1}else{0}", 1.0f);
  }

  /** Spec 2.8.2: .in(str, str, ...) — false if no match */
  @Test
  public void dotMethod_in_noMatch() {
    context.set("code", "DE");
    assertEval("if($code.in('JP','US','CA')){1}else{0}", 0.0f);
  }

  // =========================================================================
  // Spec 5.3 — null handling: isPresent()
  // =========================================================================

  /** Spec 5.3: non-existent variable returns null; isPresent returns false */
  @Test
  public void nullHandling_nonExistentVariable_isPresentFalse() {
    assertEval("if(isPresent($undeclaredVar)){1}else{0}", 0.0f);
  }

  /** Spec 5.3: non-empty string is present */
  @Test
  public void nullHandling_nonEmptyString_isPresentTrue() {
    context.set("name", "hello");
    assertEval("if(isPresent($name)){1}else{0}", 1.0f);
  }

  // =========================================================================
  // Spec 2.11 — Variable declaration: variable / var keyword
  // =========================================================================

  /** Spec 2.11: var with default value — uses default when not in context */
  @Test
  public void variableDeclaration_var_usesDefaultWhenAbsent() {
    assertEval(
        "var $amount as number set if not exists 100 description='amount';\n$amount",
        100.0f);
  }

  /** Spec 2.11: var with default — uses context value when present */
  @Test
  public void variableDeclaration_var_usesContextValueWhenPresent() {
    context.set("amount", 250.0f);
    assertEval(
        "var $amount as number set if not exists 100 description='amount';\n$amount",
        250.0f);
  }

  /** Spec 2.11: variable (long form) keyword with string default */
  @Test
  public void variableDeclaration_variable_stringDefault() {
    // $gender not set → default 'male' used
    assertEvalString(
        "variable $gender as string set if not exists 'male' description='gender';\n$gender",
        "male");
  }

  // =========================================================================
  // Spec 2.7.3 — match expression
  // =========================================================================

  /** Spec 2.7.3: match — returns first matching case */
  @Test
  public void matchExpression_returnsFirstMatchingCase() {
    context.set("countryCode", "JP");
    assertEval(
        "match{\n  $countryCode == 'JP' -> 1,\n  $countryCode == 'US' -> 2,\n  default -> 0\n}",
        1.0f);
  }

  /** Spec 2.7.3: match — falls through to default */
  @Test
  public void matchExpression_returnsDefault() {
    context.set("countryCode", "DE");
    assertEval(
        "match{\n  $countryCode == 'JP' -> 1,\n  $countryCode == 'US' -> 2,\n  default -> 0\n}",
        0.0f);
  }

  /** Spec 2.7.3: match returning string result */
  @Test
  public void matchExpression_stringResult() {
    assertEvalString(
        "match{\n  1 == 1 -> 'A',\n  default -> 'B'\n}",
        "A");
  }

  // =========================================================================
  // Spec 2.15 — Block comments in formulas
  // =========================================================================

  /** Spec 2.15: block comment before if keyword is valid */
  @Test
  public void comment_blockCommentBeforeIf() {
    assertEval("/*head*/if(true){1}else{2}", 1.0f);
  }

  /** Spec 2.15: block comment between if keyword and opening paren is valid */
  @Test
  public void comment_blockCommentBetweenIfAndParen() {
    assertEval("if/*c*/(true){1}else{2}", 1.0f);
  }

  // =========================================================================
  // Spec 2.4 — String equality/inequality uses String.equals()
  // =========================================================================

  /** Spec 2.4: string == uses String.equals() */
  @Test
  public void stringEquality_equalsOperator() {
    context.set("name", "hello");
    assertEval("if($name == 'hello'){1}else{0}", 1.0f);
  }

  /** Spec 2.4: string != uses !String.equals() */
  @Test
  public void stringEquality_notEqualsOperator() {
    context.set("name", "world");
    assertEval("if($name != 'hello'){1}else{0}", 1.0f);
  }

  // =========================================================================
  // Spec 2.8.4 — String concatenation with +
  // =========================================================================

  /** Spec 2.8.4: + with string operands performs concatenation */
  @Test
  public void stringConcatenation_variableAndLiteral() {
    context.set("name", "World");
    assertEvalString("'Hello, ' + $name", "Hello, World");
  }

  // =========================================================================
  // Spec 2.2 — Variable case sensitivity
  // =========================================================================

  /** Spec 2.2: variable names are case-sensitive: $age != $Age */
  @Test
  public void variable_caseSensitive() {
    context.set("age", 30.0f);
    // $age is set (isPresent=true), $Age is not set (isPresent=false)
    assertEval("if(isPresent($age)){1}else{0}", 1.0f);
    assertEval("if(isPresent($Age)){1}else{0}", 0.0f);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private void assertEval(String formula, float expected) {
    Calculator calc = createAstCalc(formula,
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertNotNull("result should not be null for formula: " + formula, result);
    assertTrue("expected Number for formula: " + formula, result instanceof Number);
    assertEquals("formula: " + formula, expected, ((Number) result).floatValue(), 0.01f);
  }

  private void assertEvalString(String formula, String expected) {
    Calculator calc = createAstCalc(formula,
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float));
    Object result = calc.apply(context);
    assertNotNull("result should not be null for formula: " + formula, result);
    assertEquals("formula: " + formula, expected, String.valueOf(result));
  }

  private Calculator createAstCalc(String formula, SpecifiedExpressionTypes types) {
    return CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula),
        "SpecCvg_" + Math.abs(formula.hashCode()),
        types,
        classLoader);
  }
}
