package org.unlaxer.tinyexpression.evaluator.ast;

import java.math.RoundingMode;
import java.util.List;

import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.NormalCalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.P4TypedJavaCodeEmitter;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.evaluator.javacode.VariableTypeResolver;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstAdapter;
import org.unlaxer.tinyexpression.evaluator.javacode.ast.NumberGeneratedAstNode;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.NumberFactorParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberTermParser;

/**
 * Comprehensive backend speed comparison — all 5 backends.
 *
 * Section 1 — Literal arithmetic (formula: 3+4+2+5-1):
 *   (A) compile-hand     — JavaCodeCalculatorV3: compile once -> JIT bytecode
 *   (B) ast-hand-cached  — @TinyAstNode AST, pre-parsed, eval only
 *   (C) ast-hand-full    — @TinyAstNode AST, parse+build+eval each call
 *   (D) P4-reflection    — P4 mapper + reflection-based evaluator
 *   (E) P4-typed-eval    — P4 AST + P4TypedAstEvaluator (sealed switch, no reflection)
 *
 * Section 2 — Variable formula ($a+$b+$c+$d-$e):
 *   (F) compile-hand     — JavaCodeCalculatorV3 with variables
 *   (G) AstEvaluatorCalc — full AstEvaluatorCalculator.apply() path
 *   (H) P4-typed-eval    — P4TypedAstEvaluator with manually-built variable AST
 *
 * Section 3 — Code generation (single pass, not iterated):
 *   (I) compile-dsl      — DslGeneratedAstJavaEmitter reflection rendering
 *   (J) P4-typed-emit    — P4TypedJavaCodeEmitter sealed switch rendering
 *
 * Note: Only + and - used (no * or /) because P4 mapper collapses term-level ops.
 */
public class BackendSpeedComparisonTest {

  // Use only +/- to work around P4 mapper's term-level collapse
  private static final String LITERAL_FORMULA = "3+4+2+5-1";

  private static final SpecifiedExpressionTypes TYPES =
      new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
  private static final int WARMUP  = 5_000;
  private static final int MEASURE = 50_000;

  private static CalculationContext newContext() {
    CalculationContext ctx = new NormalCalculationContext(2, RoundingMode.HALF_UP, Angle.DEGREE);
    ctx.set("a", 3.0f);
    ctx.set("b", 4.0f);
    ctx.set("c", 2.0f);
    ctx.set("d", 5.0f);
    ctx.set("e", 1.0f);
    return ctx;
  }

  @Test
  public void benchmark() {
    CalculationContext ctx = newContext();
    float expected = 13.0f; // 3+4+2+5-1

    // ═══════════════════════════════════════════════════════════════════════
    // Section 1: Literal arithmetic
    // ═══════════════════════════════════════════════════════════════════════

    // (A) compile-hand
    JavaCodeCalculatorV3 compiledLiteral = new JavaCodeCalculatorV3(
        new Source(LITERAL_FORMULA), "BenchLiteral", TYPES,
        Thread.currentThread().getContextClassLoader());
    warmAndMeasure("compile-hand", () -> compiledLiteral.calculate(ctx));
    long compileNs = measure(() -> compiledLiteral.calculate(ctx));

    // (B) ast-hand-cached
    NumberGeneratedAstNode cachedAst = buildCachedAst(LITERAL_FORMULA);
    warmAndMeasure("ast-hand-cached", () -> AstNumberExpressionEvaluator.evaluateAst(cachedAst, ExpressionTypes._float, ctx));
    long astCachedNs = measure(() -> AstNumberExpressionEvaluator.evaluateAst(cachedAst, ExpressionTypes._float, ctx));

    // (C) ast-hand-full
    warmAndMeasure("ast-hand-full", () -> AstNumberExpressionEvaluator.tryEvaluate(LITERAL_FORMULA, TYPES, ctx));
    long astFullNs = measure(() -> AstNumberExpressionEvaluator.tryEvaluate(LITERAL_FORMULA, TYPES, ctx));

    // (D) P4-reflection: parse AST via mapper, evaluate via reflection
    TinyExpressionP4AST mappedAst = TinyExpressionP4Mapper.parse(LITERAL_FORMULA, "BinaryExpr");
    warmAndMeasure("P4-reflection", () -> GeneratedP4ValueAstEvaluator.tryEvaluate(mappedAst, TYPES, ctx));
    long p4ReflectionNs = measure(() -> GeneratedP4ValueAstEvaluator.tryEvaluate(mappedAst, TYPES, ctx));

    // (E) P4-typed-eval: same AST, evaluate via sealed switch
    warmAndMeasure("P4-typed-eval", () -> {
      P4TypedAstEvaluator ev = new P4TypedAstEvaluator(TYPES, ctx);
      return ev.eval(mappedAst);
    });
    long p4TypedNs = measure(() -> {
      P4TypedAstEvaluator ev = new P4TypedAstEvaluator(TYPES, ctx);
      return ev.eval(mappedAst);
    });

    // (E2) P4-typed-eval (reuse evaluator instance)
    P4TypedAstEvaluator reusedEvaluator = new P4TypedAstEvaluator(TYPES, ctx);
    warmAndMeasure("P4-typed-reuse", () -> reusedEvaluator.eval(mappedAst));
    long p4TypedReuseNs = measure(() -> reusedEvaluator.eval(mappedAst));

    // ═══════════════════════════════════════════════════════════════════════
    // Section 2: Variable formula
    // ═══════════════════════════════════════════════════════════════════════
    String varFormula = "$a+$b+$c+$d-$e";
    float varExpected = 13.0f; // 3+4+2+5-1

    // (F) compile-hand — variable
    JavaCodeCalculatorV3 compiledVar = new JavaCodeCalculatorV3(
        new Source(varFormula), "BenchVar", TYPES,
        Thread.currentThread().getContextClassLoader());
    warmAndMeasure("compile-var", () -> compiledVar.calculate(ctx));
    long compileVarNs = measure(() -> compiledVar.calculate(ctx));

    // (G) AstEvaluatorCalculator
    AstEvaluatorCalculator astCalc = new AstEvaluatorCalculator(
        new Source(varFormula), "BenchAstCalc", TYPES,
        Thread.currentThread().getContextClassLoader());
    warmAndMeasure("AstEvalCalc", () -> astCalc.apply(ctx));
    long astCalcNs = measure(() -> astCalc.apply(ctx));

    // (H) P4-typed-eval with manually-built variable AST
    // Build: $a + $b + $c + $d - $e
    BinaryExpr varAst = new BinaryExpr(
        new BinaryExpr(
            new BinaryExpr(
                new BinaryExpr(
                    new BinaryExpr(null, List.of("$a"), List.of()),
                    List.of("+"),
                    List.of(new BinaryExpr(null, List.of("$b"), List.of()))),
                List.of("+"),
                List.of(new BinaryExpr(null, List.of("$c"), List.of()))),
            List.of("+"),
            List.of(new BinaryExpr(null, List.of("$d"), List.of()))),
        List.of("-"),
        List.of(new BinaryExpr(null, List.of("$e"), List.of())));

    P4TypedAstEvaluator varEvaluator = new P4TypedAstEvaluator(TYPES, ctx);
    warmAndMeasure("P4-typed-var", () -> varEvaluator.eval(varAst));
    long p4TypedVarNs = measure(() -> varEvaluator.eval(varAst));

    // ═══════════════════════════════════════════════════════════════════════
    // Section 3: Code generation (single-shot timing)
    // ═══════════════════════════════════════════════════════════════════════

    // (I) P4-typed-emit: generate Java code from P4 AST
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(TYPES);
    long emitStart = System.nanoTime();
    for (int i = 0; i < 10000; i++) emitter.eval(mappedAst);
    long emitNs = System.nanoTime() - emitStart;

    // (J) P4-typed-emit with variable AST
    long emitVarStart = System.nanoTime();
    for (int i = 0; i < 10000; i++) emitter.eval(varAst);
    long emitVarNs = System.nanoTime() - emitVarStart;

    // ═══════════════════════════════════════════════════════════════════════
    // Print results
    // ═══════════════════════════════════════════════════════════════════════
    double compileUs      = usPerCall(compileNs);
    double cachedUs       = usPerCall(astCachedNs);
    double fullUs         = usPerCall(astFullNs);
    double p4ReflUs       = usPerCall(p4ReflectionNs);
    double p4TypedUs      = usPerCall(p4TypedNs);
    double p4TypedReuseUs = usPerCall(p4TypedReuseNs);
    double compileVarUs   = usPerCall(compileVarNs);
    double astCalcUs      = usPerCall(astCalcNs);
    double p4TypedVarUs   = usPerCall(p4TypedVarNs);

    System.out.println("\n" + "=".repeat(72));
    System.out.println(" Backend Speed Comparison");
    System.out.println("=".repeat(72));
    System.out.printf("iterations: %,d  (warmup: %,d)%n%n", MEASURE, WARMUP);

    System.out.println("--- Section 1: Literal arithmetic [" + LITERAL_FORMULA + "] ---");
    System.out.printf("(A) compile-hand   [JVM bytecode]    : %8.4f us/call  (baseline)%n", compileUs);
    System.out.printf("(B) ast-hand-cached[tree eval only]  : %8.4f us/call  x%.1f%n", cachedUs, cachedUs / compileUs);
    System.out.printf("(C) ast-hand-full  [parse+build+eval]: %8.4f us/call  x%.1f%n", fullUs, fullUs / compileUs);
    System.out.printf("(D) P4-reflection  [mapper+reflect]  : %8.4f us/call  x%.1f%n", p4ReflUs, p4ReflUs / compileUs);
    System.out.printf("(E) P4-typed-eval  [sealed switch]   : %8.4f us/call  x%.1f%n", p4TypedUs, p4TypedUs / compileUs);
    System.out.printf("(E2)P4-typed-reuse [instance reused] : %8.4f us/call  x%.1f%n%n", p4TypedReuseUs, p4TypedReuseUs / compileUs);

    System.out.println("--- Section 2: Variable formula [$a+$b+$c+$d-$e] ---");
    System.out.printf("(F) compile-hand   [JVM bytecode]    : %8.4f us/call  (baseline)%n", compileVarUs);
    System.out.printf("(G) AstEvalCalc    [full path]       : %8.4f us/call  x%.1f%n", astCalcUs, astCalcUs / compileVarUs);
    System.out.printf("(H) P4-typed-var   [sealed switch]   : %8.4f us/call  x%.1f%n%n", p4TypedVarUs, p4TypedVarUs / compileVarUs);

    System.out.println("--- Section 3: Code generation [10,000 iterations] ---");
    System.out.printf("(I) P4-typed-emit  [literal]         : %8.4f us/call%n", (double) emitNs / 10000 / 1000.0);
    System.out.printf("(J) P4-typed-emit  [variable]        : %8.4f us/call%n", (double) emitVarNs / 10000 / 1000.0);
    System.out.println("=".repeat(72));
  }

  private interface BenchRunnable {
    Object run();
  }

  private void warmAndMeasure(String name, BenchRunnable r) {
    for (int i = 0; i < WARMUP; i++) r.run();
  }

  private long measure(BenchRunnable r) {
    long t = System.nanoTime();
    for (int i = 0; i < MEASURE; i++) r.run();
    return System.nanoTime() - t;
  }

  private static double usPerCall(long totalNs) {
    return (double) totalNs / MEASURE / 1000.0;
  }

  private static NumberGeneratedAstNode buildCachedAst(String formula) {
    Parser parser = Parser.get(TinyExpressionParser.class);
    ParseContext parseContext = new ParseContext(new StringSource(formula));
    try (parseContext) {
      Parsed parsed = parser.parse(parseContext);
      Token root = parsed.getRootToken(true);
      root = VariableTypeResolver.resolveVariableType(root);
      root = OperatorOperandTreeCreator.SINGLETON.apply(root);
      TinyExpressionTokens tokens = new TinyExpressionTokens(root, TYPES);
      Token expr = unwrapNumberExpressionToken(tokens.getExpressionToken());
      return NumberGeneratedAstAdapter.SINGLETON.generateOrThrow(expr);
    }
  }

  private static Token unwrapNumberExpressionToken(Token token) {
    if (token == null) return null;
    if (token.parser instanceof NumberExpressionParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    if (token.parser instanceof NumberTermParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    if (token.parser instanceof NumberFactorParser && !token.filteredChildren.isEmpty()) {
      token = token.filteredChildren.get(0);
    }
    return token;
  }
}
