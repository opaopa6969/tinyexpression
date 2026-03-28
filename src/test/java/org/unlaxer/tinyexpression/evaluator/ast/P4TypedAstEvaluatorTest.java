package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.*;

import java.math.RoundingMode;
import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.CalculationContext.Angle;
import org.unlaxer.tinyexpression.NormalCalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class P4TypedAstEvaluatorTest {

  private static CalculationContext newContext() {
    CalculationContext ctx = new NormalCalculationContext(2, RoundingMode.HALF_UP, Angle.DEGREE);
    ctx.set("a", 3.0f);
    ctx.set("b", 4.0f);
    ctx.set("x", 10.0f);
    ctx.set("flag", true);
    ctx.set("name", "hello");
    return ctx;
  }

  private static BinaryExpr leaf(String literal) {
    return new BinaryExpr(null, List.of(literal), List.of());
  }

  private static BinaryExpr wrap(BinaryExpr inner) {
    return new BinaryExpr(inner, List.of(), List.of());
  }

  private static BinaryExpr binary(BinaryExpr left, String op, BinaryExpr right) {
    return new BinaryExpr(left, List.of(op), List.of(right));
  }

  /** Wrap a raw boolean literal/variable text into BooleanOrExpr(BooleanAndExpr(BooleanXorExpr(BooleanFactorExpr(text)))). */
  private static BooleanOrExpr boolWrap(String text) {
    BooleanFactorExpr factor = new BooleanFactorExpr(text);
    BooleanXorExpr xor = new BooleanXorExpr(factor, List.of(), List.of());
    BooleanAndExpr and = new BooleanAndExpr(xor, List.of(), List.of());
    return new BooleanOrExpr(and, List.of(), List.of());
  }

  /** Wrap a mapped AST node (e.g. ComparisonExpr) into BooleanOrExpr. */
  private static BooleanOrExpr boolWrap(TinyExpressionP4AST astNode) {
    BooleanFactorExpr factor = new BooleanFactorExpr(astNode);
    BooleanXorExpr xor = new BooleanXorExpr(factor, List.of(), List.of());
    BooleanAndExpr and = new BooleanAndExpr(xor, List.of(), List.of());
    return new BooleanOrExpr(and, List.of(), List.of());
  }

  // ── Numeric literal ──

  @Test
  public void testLiteralNumber() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(leaf("3.5"));
    assertEquals(3.5f, ((Number) result).floatValue(), 0.001f);
  }

  // ── Arithmetic: 3*4+2 = 14 ──

  @Test
  public void testBinaryArithmetic() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    // 3*4+2 → BinaryExpr(left=BinaryExpr(left=leaf(3), op=[*], right=[leaf(4)]), op=[+], right=[leaf(2)])
    BinaryExpr mul = binary(leaf("3"), "*", leaf("4"));
    BinaryExpr add = binary(mul, "+", leaf("2"));
    Object result = evaluator.eval(add);
    assertEquals(14.0f, ((Number) result).floatValue(), 0.001f);
  }

  // ── Variable in BinaryExpr: $a * $b = 12 ──

  @Test
  public void testVariableInBinaryExpr() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    BinaryExpr mul = binary(leaf("$a"), "*", leaf("$b"));
    Object result = evaluator.eval(mul);
    assertEquals(12.0f, ((Number) result).floatValue(), 0.001f);
  }

  // ── VariableRefExpr ──

  @Test
  public void testVariableRefExprNumber() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new VariableRefExpr("$x"));
    assertEquals(10.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testVariableRefExprString() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new VariableRefExpr("$name"));
    assertEquals("hello", result);
  }

  @Test
  public void testVariableRefExprBoolean() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new VariableRefExpr("$flag"));
    assertEquals(true, result);
  }

  // ── ComparisonExpr ──

  @Test
  public void testComparisonGreaterThan() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), newContext());
    ComparisonExpr comp = new ComparisonExpr(leaf("$x"), ">", leaf("5"));
    Object result = evaluator.eval(comp);
    assertEquals(true, result);
  }

  @Test
  public void testComparisonEqual() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), newContext());
    ComparisonExpr comp = new ComparisonExpr(leaf("$a"), "==", leaf("3"));
    Object result = evaluator.eval(comp);
    assertEquals(true, result);
  }

  // ── BooleanOrExpr with literal ──

  @Test
  public void testBooleanExprLiteral() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(boolWrap("true"));
    assertEquals(true, result);
  }

  // ── BooleanOrExpr wrapping ComparisonExpr ──

  @Test
  public void testBooleanExprWithComparison() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), newContext());
    ComparisonExpr comp = new ComparisonExpr(leaf("$a"), "<", leaf("$b"));
    Object result = evaluator.eval(boolWrap(comp));
    assertEquals(true, result);  // 3 < 4
  }

  // ── IfExpr: if($a < $b) then 100 else 200 ──

  @Test
  public void testIfExprThenBranch() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    ComparisonExpr comp = new ComparisonExpr(leaf("$a"), "<", leaf("$b"));
    BooleanOrExpr condition = boolWrap(comp);
    ExpressionExpr thenExpr = new ExpressionExpr(leaf("100"));
    ExpressionExpr elseExpr = new ExpressionExpr(leaf("200"));
    IfExpr ifExpr = new IfExpr(condition, thenExpr, elseExpr);
    Object result = evaluator.eval(ifExpr);
    assertEquals(100.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprElseBranch() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    ComparisonExpr comp = new ComparisonExpr(leaf("$a"), ">", leaf("$b"));
    BooleanOrExpr condition = boolWrap(comp);
    ExpressionExpr thenExpr = new ExpressionExpr(leaf("100"));
    ExpressionExpr elseExpr = new ExpressionExpr(leaf("200"));
    IfExpr ifExpr = new IfExpr(condition, thenExpr, elseExpr);
    Object result = evaluator.eval(ifExpr);
    assertEquals(200.0f, ((Number) result).floatValue(), 0.001f);
  }

  // ── StringExpr ──

  @Test
  public void testStringExprLiteral() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new StringExpr("hello world"));
    assertEquals("hello world", result);
  }

  // ── NumberMatchExpr ──

  @Test
  public void testNumberMatchExpr() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());

    // match { case($a > 5) => 100; case($a > 2) => 200; default => 300 }
    NumberCaseExpr case1 = new NumberCaseExpr(
        boolWrap(new ComparisonExpr(leaf("$a"), ">", leaf("5"))),
        new NumberCaseValueExpr(leaf("100")));
    NumberCaseExpr case2 = new NumberCaseExpr(
        boolWrap(new ComparisonExpr(leaf("$a"), ">", leaf("2"))),
        new NumberCaseValueExpr(leaf("200")));
    NumberDefaultCaseExpr defaultCase = new NumberDefaultCaseExpr(new NumberCaseValueExpr(leaf("300")));

    NumberMatchExpr matchExpr = new NumberMatchExpr(case1, List.of(case2), defaultCase);
    Object result = evaluator.eval(matchExpr);
    // $a=3, 3>5=false, 3>2=true → 200
    assertEquals(200.0f, ((Number) result).floatValue(), 0.001f);
  }

  // ── ExpressionExpr unwrap ──

  @Test
  public void testExpressionExprUnwrap() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    ExpressionExpr expr = new ExpressionExpr(leaf("42"));
    Object result = evaluator.eval(expr);
    assertEquals(42.0f, ((Number) result).floatValue(), 0.001f);
  }

  // ── Int type ──

  @Test
  public void testIntType() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._int, ExpressionTypes._int), newContext());
    BinaryExpr add = binary(leaf("3"), "+", leaf("4"));
    Object result = evaluator.eval(add);
    assertEquals(7, ((Number) result).intValue());
  }

  // ── String dot methods ──

  @Test
  public void testToUpperCaseDotMethod() {
    // $name.toUpperCase() → "HELLO"
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new ToUpperCaseDotExpr(new VariableRefExpr("$name")));
    assertEquals("HELLO", result);
  }

  @Test
  public void testToLowerCaseDotMethod() {
    // $name.toLowerCase() → "hello" (already lowercase)
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new ToLowerCaseDotExpr(new VariableRefExpr("$name")));
    assertEquals("hello", result);
  }

  @Test
  public void testTrimDotMethod() {
    // $spacey.trim() → "hello"
    CalculationContext ctx = newContext();
    ctx.set("spacey", "  hello  ");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new TrimDotExpr(new VariableRefExpr("$spacey")));
    assertEquals("hello", result);
  }

  @Test
  public void testLengthDotMethod() {
    // $name.length() → 5.0
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new LengthDotExpr(new VariableRefExpr("$name")));
    assertEquals(5.0f, ((Number) result).floatValue(), 0.001f);
  }
}
