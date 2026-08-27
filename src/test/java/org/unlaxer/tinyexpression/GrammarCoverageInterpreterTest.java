package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Comprehensive grammar-derived coverage, run on the FAST P4-typed interpreter
 * (AST_EVALUATOR backend). Every case is a real formula paired with its runtime
 * answer. Heavy emphasis on boolean nesting (not / nested & | ^) and operator
 * precedence, which is the area users reported problems with.
 *
 * Boolean precedence follows the UBNF grammar: OR (loosest) &lt; AND &lt; XOR (tightest).
 * i.e. `a | b & c`  == `a | (b & c)` ; `a ^ b & c` == `(a ^ b) & c` ... wait — XOR is
 * tightest so `a & b ^ c` == `a & (b ^ c)` and `a ^ b & c` == `(a ^ b) & c`.
 *
 * Comparisons combined with boolean operators are written inside if(...) because a
 * bare top-level `1>0 & 2>1` is shadowed by NumberExpression in the Expression rule
 * (documented limitation — see findings doc).
 */
public class GrammarCoverageInterpreterTest {

  private final ClassLoader cl = Thread.currentThread().getContextClassLoader();

  // ───────────────────────── arithmetic ─────────────────────────
  @Test public void arithmetic() {
    assertNum("1+1", 2);
    assertNum("2+3*4", 14);
    assertNum("(2+3)*4", 20);
    assertNum("10-2*3", 4);
    assertNum("1+8/4", 3);
    assertNum("(10-2)*(7-3)", 32);
    assertNum("2*3+4*5", 26);
    assertNum("100/10/2", 5);
    assertNum("2-3-4", -5);            // left assoc
    assertNum("3.5+1.5", 5);
    assertNum("10/4", 2.5f);
  }

  // ───────────────────────── math functions ─────────────────────
  @Test public void mathFunctions() {
    assertNum("sqrt(9)", 3);
    assertNum("abs(-7)", 7);
    assertNum("abs(5)", 5);
    assertNum("round(2.6)", 3);
    assertNum("round(2.4)", 2);
    assertNum("floor(3.9)", 3);
    assertNum("floor(-1.1)", -2);
    assertNum("ceil(3.1)", 4);
    assertNum("ceil(-3.9)", -3);
    assertNum("pow(2,10)", 1024);
    assertNum("pow(3,2)", 9);
    assertNum("min(3,5)", 3);
    assertNum("max(3,5)", 5);
    // NOTE: min/max with >2 args are broken — see KnownP4BugsTest.variadicMinMax.
    assertNum("log(1)", 0);
    assertNum("exp(0)", 1);
    // NOTE: `abs(-3)+pow(2,3)` (arithmetic combining function-call terms) is broken by
    // the cross-check — see KnownP4BugsTest.crossCheckDropsFunctionArithmetic.
  }

  // ───────────────────────── boolean literals & single ops ──────
  @Test public void booleanSingleOps() {
    assertBool("true", true);
    assertBool("false", false);
    assertBool("true & false", false);
    assertBool("true & true", true);
    assertBool("true | false", true);
    assertBool("false | false", false);
    assertBool("true ^ false", true);
    assertBool("true ^ true", false);
    assertBool("not(true)", false);
    assertBool("not(false)", true);
  }

  // ───────────────────────── boolean PRECEDENCE (OR<AND<XOR) ─────
  @Test public void booleanPrecedence() {
    // AND binds tighter than OR: a | b & c == a | (b&c)
    assertBool("true | false & false", true);
    assertBool("false | true & true", true);
    assertBool("false | false & true", false);
    // XOR binds tighter than AND: a & b ^ c == a & (b^c)
    assertBool("true & false ^ true", true);     // true & (false^true) = true&true
    assertBool("true & true ^ true", false);     // true & (true^true) = true&false
    // XOR binds tighter than AND: a ^ b & c == (a^b)... no, & looser than ^, so b&c?
    // precedence AND(2)<XOR(3): ^ tighter -> a ^ (b & c)? NO: tighter operator groups first.
    // "true ^ true & false": ^ is tighter so (true^true) then &false = false&false=false
    assertBool("true ^ true & false", false);
    assertBool("true ^ false & false", false);   // (true^false)&false = true&false
    assertBool("false | true ^ true", false);    // false | (true^true) = false|false
    // long mixed chain
    assertBool("true | false & false | true", true);
    assertBool("false ^ false | true & true", true); // (false^false)? no: | loosest. (false^false) | (true&true) = false|true
  }

  // ───────────────────────── not nesting (nasty) ────────────────
  @Test public void notNesting() {
    assertBool("not(not(true))", true);
    assertBool("not(not(not(true)))", false);
    assertBool("not(true & false)", true);
    assertBool("not(true | false)", false);
    assertBool("not(true ^ false)", false);
    assertBool("not(true) & not(false)", false);
    assertBool("not(false) | not(true)", true);
    assertBool("not(true) | not(false)", true);
    assertBool("not(not(true) & not(false))", true);
    assertBool("not(true & true) | not(false)", true);
  }

  // ───────────────────────── parenthesised regrouping ───────────
  @Test public void booleanParens() {
    assertBool("(true | false) & false", false);
    assertBool("true | (false & false)", true);
    assertBool("(true ^ false) & false", false);
    assertBool("true ^ (false & false)", true);
    assertBool("(true | false) & (false | true)", true);
    assertBool("not((true | false) & false)", true);
  }

  // ───────────────────────── comparisons inside if ──────────────
  @Test public void comparisonsInIf() {
    assertNum("if(1>0){1}else{0}", 1);
    assertNum("if(0>1){1}else{0}", 0);
    assertNum("if(1>=1){1}else{0}", 1);
    assertNum("if(1<=0){1}else{0}", 0);
    assertNum("if(2==2){1}else{0}", 1);
    assertNum("if(2!=3){1}else{0}", 1);
    assertNum("if(1>0 & 2>1){1}else{0}", 1);
    assertNum("if(1>0 & 2>3){1}else{0}", 0);
    assertNum("if(1>0 | 2>3){1}else{0}", 1);
    assertNum("if(1>2 | 3>4){1}else{0}", 0);
    assertNum("if(1>0 & 2>1 & 3>2){1}else{0}", 1);
    // NOTE: `if(1>0 | 0>1 & 1>2)` (precedence with comparisons) is broken by the
    // cross-check — see KnownP4BugsTest.crossCheckOverridesCorrectP4Precedence.
    assertNum("if((1>0 | 0>1) & 1>2){1}else{0}", 0);  // (T|F)&F = F
    assertNum("if(not(1>2)){1}else{0}", 1);
    assertNum("if(not(1>0)){1}else{0}", 0);
    assertNum("if(not(1>2) & not(3>4)){1}else{0}", 1);
  }

  // ───────────────────────── if / ternary / nested ──────────────
  @Test public void controlFlow() {
    assertNum("if(true){1}else{2}", 1);
    assertNum("if(false){1}else{2}", 2);
    assertNum("if(2+3>4){1}else{0}", 1);
    assertNum("(true ? 1 : 2)", 1);
    assertNum("(false ? 1 : 2)", 2);
    assertNum("(2>1 ? 10 : 20)", 10);
    assertNum("if(true){if(false){1}else{2}}else{3}", 2);
    // NOTE: nested ternary `(true ? (false ? 1 : 2) : 3)` is broken — see
    // KnownP4BugsTest.nestedTernary.
    assertNum("abs((true ? -5 : 5))", 5);
  }

  // ───────────────────────── number match ───────────────────────
  @Test public void numberMatch() {
    assertNum("match{ true -> 1, default -> 9 }", 1);
    assertNum("match{ false -> 1, default -> 9 }", 9);
    assertNum("match{ 1>2 -> 1, 2>1 -> 2, default -> 9 }", 2);
    assertNum("match{ 1>2 -> 1, 2>3 -> 2, default -> 9 }", 9);
  }

  // ───────────────────────── strings ────────────────────────────
  @Test public void strings() {
    assertStr("'hello'", "hello");
    assertStr("'a' + 'b'", "ab");
    assertStr("'a' + 'b' + 'c'", "abc");
    assertStr("toUpperCase('abc')", "ABC");
    assertStr("toLowerCase('ABC')", "abc");
    assertStr("trim('  x  ')", "x");
    assertNum("length('hello')", 5);
  }

  @Test public void stringPredicatesInIf() {
    assertNum("if('hello' == 'hello'){1}else{0}", 1);
    assertNum("if('hello' != 'world'){1}else{0}", 1);
    assertNum("if(startsWith('hello','he')){1}else{0}", 1);
    assertNum("if(endsWith('hello','lo')){1}else{0}", 1);
    assertNum("if(contains('hello','ell')){1}else{0}", 1);
    assertNum("if(startsWith('hello','xx')){1}else{0}", 0);
  }

  // ───────────────────────── variables (context) ────────────────
  @Test public void variables() {
    CalculationContext c = CalculationContext.newConcurrentContext();
    c.set("x", 10f);
    c.set("y", 3f);
    assertNumCtx("$x+$y", 13, c);
    assertNumCtx("$x*$y", 30, c);
    assertNumCtx("if($x>$y){1}else{0}", 1, c);
    assertNumCtx("if($x>$y & $y>0){1}else{0}", 1, c);
  }

  @Test public void contextValuesAreFreshAcrossApply() {
    // guards the AST cache: same Calculator, different context -> different result
    Calculator calc = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source("$x+1"), "CacheCtx",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), cl);
    CalculationContext c1 = CalculationContext.newConcurrentContext();
    c1.set("x", 5f);
    CalculationContext c2 = CalculationContext.newConcurrentContext();
    c2.set("x", 100f);
    assertEquals(6f, ((Number) calc.apply(c1)).floatValue(), 0.001f);
    assertEquals(101f, ((Number) calc.apply(c2)).floatValue(), 0.001f);  // not stale-cached at 6
    assertEquals(6f, ((Number) calc.apply(c1)).floatValue(), 0.001f);
  }

  // ───────────────────────── helpers ────────────────────────────
  private void assertNum(String formula, float expected) {
    Object r = evalNum(formula, CalculationContext.newConcurrentContext());
    assertNotNull("null for: " + formula, r);
    assertTrue("not a Number for: " + formula + " -> " + r, r instanceof Number);
    assertEquals(formula, expected, ((Number) r).floatValue(), 0.01f);
  }

  private void assertNumCtx(String formula, float expected, CalculationContext ctx) {
    Object r = evalNum(formula, ctx);
    assertNotNull("null for: " + formula, r);
    assertEquals(formula, expected, ((Number) r).floatValue(), 0.01f);
  }

  private Object evalNum(String formula, CalculationContext ctx) {
    Calculator calc = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "Cov_" + Math.abs(formula.hashCode()),
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), cl);
    return calc.apply(ctx);
  }

  private void assertBool(String formula, boolean expected) {
    Calculator calc = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "CovB_" + Math.abs(formula.hashCode()),
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), cl);
    Object r = calc.apply(CalculationContext.newConcurrentContext());
    assertNotNull("null for: " + formula, r);
    assertEquals(formula, expected, r);
  }

  private void assertStr(String formula, String expected) {
    Calculator calc = CalculatorCreatorRegistry.astEvaluatorCreator().create(
        new Source(formula), "CovS_" + Math.abs(formula.hashCode()),
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), cl);
    Object r = calc.apply(CalculationContext.newConcurrentContext());
    assertEquals(formula, expected, String.valueOf(r));
  }
}
