package org.unlaxer.tinyexpression;

import org.junit.Ignore;
import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreator;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * NON-ASSERTING micro-benchmark: compares P4 interpreter (AST_EVALUATOR / P4-typed)
 * vs compiled javacode (JAVA_CODE) for create-time and per-apply-time across
 * expression sizes (small / ~KB / ~tens-of-KB).
 *
 * Findings to read from stdout:
 *  - javacode pays a large one-time javac cost that grows with formula size and
 *    can hit the JVM 64KB-per-method bytecode limit for very large formulas.
 *  - the interpreter currently RE-PARSES + RE-MAPS on every apply() (no AST cache),
 *    so its per-apply cost grows with size and is paid on every evaluation.
 */
public class BackendSpeedBenchmarkTest {

  /** number of '+1' terms -> rough size control */
  static final int[] TERMS = {1, 200, 2000, 6000};

  @Ignore("manual performance benchmark — run explicitly with -Dtest=BackendSpeedBenchmarkTest")
  @Test
  public void benchmark() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    SpecifiedExpressionTypes num =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);

    System.out.println("\n================= BACKEND SPEED BENCHMARK =================");
    System.out.printf("%-9s %-7s | %-22s | %-22s%n", "terms", "bytes", "INTERPRETER (p4-typed)", "JAVACODE (compiled)");
    System.out.printf("%-9s %-7s | %-10s %-11s | %-10s %-11s%n", "", "", "create", "apply(avg)", "create", "apply(avg)");
    System.out.println("----------------------------------------------------------------------------");

    for (int n : TERMS) {
      String formula = buildArith(n);
      Row interp = measure(CalculatorCreatorRegistry.astEvaluatorCreator(), formula, num, cl);
      Row java = measure(CalculatorCreatorRegistry.javaCodeCreator(), formula, num, cl);
      System.out.printf("%-9d %-7d | %-10s %-11s | %-10s %-11s%n",
          n, formula.length(), interp.create, interp.apply, java.create, java.apply);
    }
    System.out.println("============================================================\n");
  }

  static String buildArith(int terms) {
    StringBuilder sb = new StringBuilder("1");
    for (int i = 1; i < terms; i++) sb.append("+1");
    return sb.toString();
  }

  static class Row {
    String create = "-";
    String apply = "-";
  }

  private Row measure(CalculatorCreator creator, String formula, SpecifiedExpressionTypes types, ClassLoader cl) {
    Row row = new Row();
    Calculator calc;
    try {
      long t0 = System.nanoTime();
      calc = creator.create(new Source(formula), "Bench_" + Math.abs(formula.hashCode()), types, cl);
      // create() may be lazy; force the first apply to include compile/parse cost
      Object first = calc.apply(CalculationContext.newConcurrentContext());
      long t1 = System.nanoTime();
      row.create = ms(t1 - t0) + (first == null ? "(null)" : "");
    } catch (Throwable t) {
      row.create = "ERR:" + t.getClass().getSimpleName();
      return row;
    }
    try {
      // warm apply: average over iterations
      int iters = 20;
      CalculationContext ctx = CalculationContext.newConcurrentContext();
      // a couple of warmups
      for (int i = 0; i < 3; i++) calc.apply(ctx);
      long t0 = System.nanoTime();
      for (int i = 0; i < iters; i++) calc.apply(ctx);
      long t1 = System.nanoTime();
      row.apply = ms((t1 - t0) / iters);
    } catch (Throwable t) {
      row.apply = "ERR:" + t.getClass().getSimpleName();
    }
    return row;
  }

  static String ms(long nanos) {
    return String.format("%.2fms", nanos / 1_000_000.0);
  }
}
