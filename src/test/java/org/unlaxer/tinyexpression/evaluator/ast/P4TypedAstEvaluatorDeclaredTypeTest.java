package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;

import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.ConcurrentCalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanEqualityExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.VariableRefExpr;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Direct, shadow-independent coverage of declared-type-aware equality on the pure-AST path
 * (#32 / handoff #44 "C"). The if-source shadow rescues if-conditions at runtime, so this test
 * exercises {@code evalBooleanEqualityExpr} directly to lock in the behavior regardless of the
 * shadow (which is gated for removal on the parse-performance work, #38).
 */
public class P4TypedAstEvaluatorDeclaredTypeTest {

  private CalculationContext context() {
    CalculationContext context = new ConcurrentCalculationContext(2, RoundingMode.HALF_UP, Angle.DEGREE);
    context.set("name", "opa");
    context.set("remitterAccountHolderKana", "opai");
    return context;
  }

  private Object evalEquality(Map<String, ExpressionType> declaredTypes) {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float),
        context(), null, null, declaredTypes);
    BooleanEqualityExpr node =
        new BooleanEqualityExpr(new VariableRefExpr("name", Optional.empty()), "==",
            new VariableRefExpr("remitterAccountHolderKana", Optional.empty()));
    return evaluator.eval(node);
  }

  /**
   * With {@code var $name as string}, "opa" == "opai" must be a STRING comparison → false.
   * (Previously the operands were coerced to boolean → false == false → true.)
   */
  @Test
  public void declaredStringVariableForcesStringEquality() {
    assertEquals(false, evalEquality(Map.of("name", ExpressionTypes.string)));
  }

  /**
   * Without a declared type, behavior is unchanged: operands coerce to boolean
   * (number/string == is the legacy default), so two non-boolean values compare equal.
   */
  @Test
  public void undeclaredVariablesKeepBooleanEqualityBehavior() {
    assertEquals(true, evalEquality(Map.of()));
  }
}
