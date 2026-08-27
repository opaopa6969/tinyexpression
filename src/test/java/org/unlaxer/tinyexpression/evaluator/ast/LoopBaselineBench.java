package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.RoundingMode;

import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.NormalCalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Lightweight LOOP-benchmark: measures AstEvaluatorCalculator.apply() hot path only.
 * Not a JUnit assertion test; run via main() for quick baseline numbers.
 */
public class LoopBaselineBench {

  public static void main(String[] args) {
    // Representative production-like formula (variable arithmetic + if)
    String formula = "if($age >= 20){100}else{0}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);

    CalculationContext ctx = new NormalCalculationContext(2, RoundingMode.HALF_UP, Angle.DEGREE);
    ctx.set("age", 30);

    AstEvaluatorCalculator calc = new AstEvaluatorCalculator(
        new Source(formula), "LoopBench", types,
        Thread.currentThread().getContextClassLoader());

    int warmup = 200;
    int measure = 1_000;

    // Warmup
    for (int i = 0; i < warmup; i++) {
      Object r = calc.apply(ctx);
      if (r == null) throw new RuntimeException("null");
    }

    // Measure apply() — includes re-parse every call (no cache on master)
    long t1 = System.nanoTime();
    for (int i = 0; i < measure; i++) {
      Object r = calc.apply(ctx);
      if (r == null) throw new RuntimeException("null");
    }
    long applyNs = System.nanoTime() - t1;
    double applyUs = applyNs / (double) measure / 1000.0;

    // Measure pure eval (parse once, eval many) — lower bound
    TinyExpressionP4AST ast = org.unlaxer.tinyexpression.p4.P4PreferredAstMapper.parse(
        formula, ExpressionTypes._float);
    P4TypedAstEvaluator ev = new P4TypedAstEvaluator(types, ctx, formula,
        Thread.currentThread().getContextClassLoader());
    for (int i = 0; i < warmup; i++) {
      Object r = ev.eval(ast);
      if (r == null) throw new RuntimeException("null");
    }
    long t2 = System.nanoTime();
    for (int i = 0; i < measure; i++) {
      Object r = ev.eval(ast);
      if (r == null) throw new RuntimeException("null");
    }
    long evalNs = System.nanoTime() - t2;
    double evalUs = evalNs / (double) measure / 1000.0;

    // Measure parse-only cost (parse fresh each iteration) — fewer iterations, parse is heavy
    int parseMeasure = 200;
    long t3 = System.nanoTime();
    for (int i = 0; i < parseMeasure; i++) {
      TinyExpressionP4AST a = org.unlaxer.tinyexpression.p4.P4PreferredAstMapper.parse(
          formula, ExpressionTypes._float);
      if (a == null) throw new RuntimeException("null parse");
    }
    long parseNs = System.nanoTime() - t3;
    double parseUs = parseNs / (double) parseMeasure / 1000.0;

    System.out.println();
    System.out.println("=".repeat(60));
    System.out.println("LOOP Baseline (formula: " + formula + ")");
    System.out.println("iterations: " + measure + " (warmup: " + warmup + ")");
    System.out.printf("apply()  [parse+eval each]: %8.4f us/call%n", applyUs);
    System.out.printf("eval only [pre-parsed AST] : %8.4f us/call%n", evalUs);
    System.out.printf("parse only [mapper only]   : %8.4f us/call%n", parseUs);
    System.out.printf("parse share of apply: %.1f%%%n", 100.0 * parseUs / applyUs);
    System.out.println("=".repeat(60));
  }
}
