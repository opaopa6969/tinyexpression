package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Parity proof: for the set of expressions both backends fully support, the P4-typed
 * INTERPRETER (AST_EVALUATOR) and the compiled JAVA_CODE backend produce identical
 * results. This is the justification for running the bulk of coverage on the fast
 * interpreter (GrammarCoverageInterpreterTest).
 *
 * NOTE: parity does NOT hold for every expression — see findings-2026-06-15 §5
 * (mixed boolean operator precedence: interpreter follows the grammar OR&lt;AND&lt;XOR,
 * legacy/javacode uses flat left-assoc) and §6 (P4 mapper/cross-check bugs). Those
 * divergent cases are deliberately excluded here and tracked in KnownP4BugsTest.
 */
public class InterpreterJavacodeParityTest {

  private final ClassLoader cl = Thread.currentThread().getContextClassLoader();

  // NOTE: the JAVA_CODE backend does NOT support the math functions (abs/sqrt/round/
  // ceil/floor/pow/min/max) that the interpreter supports — `Unsupported parser in
  // factor: AbsParser`. So parity is only provable on the common subset below; the
  // interpreter is strictly more capable (findings §4/§8).
  static final String[] NUMERIC = {
      "1+1", "2+3*4", "(2+3)*4", "10-2*3", "1+8/4", "(10-2)*(7-3)",
      "2-3-4", "100/10/2",
      "if(true){1}else{2}", "if(2+3>4){1}else{0}",
      "if(1>0 & 2>1){1}else{0}", "if(1>2 | 3>4){1}else{0}",
      "if(not(1>2)){1}else{0}",
      "match{ true -> 1, default -> 9 }",
      "if('a' == 'a'){1}else{0}",
  };

  static final String[] BOOLEAN = {
      "true", "false", "true & false", "true | false", "true ^ false",
      "not(true)", "not(false)", "not(not(true))",
  };

  static final String[] STRING = {
      "'hello'", "'a' + 'b'", "toUpperCase('abc')", "toLowerCase('ABC')", "trim('  x  ')",
  };

  @Test public void numericParity() {
    for (String f : NUMERIC) assertParity(f, ExpressionTypes._float);
  }

  @Test public void booleanParity() {
    for (String f : BOOLEAN) assertParity(f, ExpressionTypes._boolean);
  }

  @Test public void stringParity() {
    for (String f : STRING) assertParity(f, ExpressionTypes.string);
  }

  private void assertParity(String formula, ExpressionTypes type) {
    SpecifiedExpressionTypes t = new SpecifiedExpressionTypes(type, ExpressionTypes._float);
    Object interp = CalculatorCreatorRegistry.astEvaluatorCreator()
        .create(new Source(formula), "ParI_" + Math.abs(formula.hashCode()), t, cl)
        .apply(CalculationContext.newConcurrentContext());
    Object java = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "ParJ_" + Math.abs(formula.hashCode()), t, cl)
        .apply(CalculationContext.newConcurrentContext());
    assertEquals("parity mismatch for: " + formula,
        String.valueOf(normalize(interp)), String.valueOf(normalize(java)));
  }

  /** normalize numeric types so 3 (Integer) and 3.0 (Float) compare equal. */
  private Object normalize(Object o) {
    if (o instanceof Number n) return n.doubleValue();
    return o;
  }
}
