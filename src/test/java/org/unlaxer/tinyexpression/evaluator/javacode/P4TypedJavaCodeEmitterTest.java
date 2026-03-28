package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.*;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class P4TypedJavaCodeEmitterTest {

  private static BinaryExpr leaf(String literal) {
    return new BinaryExpr(null, List.of(literal), List.of());
  }

  private static BinaryExpr binary(BinaryExpr left, String op, BinaryExpr right) {
    return new BinaryExpr(left, List.of(op), List.of(right));
  }

  private static BooleanOrExpr boolWrap(String text) {
    BooleanFactorExpr factor = new BooleanFactorExpr(text);
    BooleanXorExpr xor = new BooleanXorExpr(factor, List.of(), List.of());
    BooleanAndExpr and = new BooleanAndExpr(xor, List.of(), List.of());
    return new BooleanOrExpr(and, List.of(), List.of());
  }

  // ── Numeric literal ──

  @Test
  public void testLiteralNumber() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    String code = emitter.eval(leaf("3.5"));
    assertEquals("3.5f", code);
  }

  // ── Arithmetic: 3*4+2 ──

  @Test
  public void testBinaryArithmetic() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    BinaryExpr mul = binary(leaf("3"), "*", leaf("4"));
    BinaryExpr add = binary(mul, "+", leaf("2"));
    String code = emitter.eval(add);
    assertEquals("((3.0f*4.0f)+2.0f)", code);
  }

  // ── Variable in BinaryExpr: $a * $b ──

  @Test
  public void testVariableInBinaryExpr() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    BinaryExpr mul = binary(leaf("$a"), "*", leaf("$b"));
    String code = emitter.eval(mul);
    assertTrue("Should contain getNumber for $a", code.contains("getNumber(\"a\")"));
    assertTrue("Should contain getNumber for $b", code.contains("getNumber(\"b\")"));
    assertTrue("Should contain * operator", code.contains("*"));
  }

  // ── VariableRefExpr ──

  @Test
  public void testVariableRefExprNumber() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    String code = emitter.eval(new VariableRefExpr("$x"));
    assertTrue("Should generate getNumber", code.contains("getNumber(\"x\")"));
    assertTrue("Should have float fallback", code.contains("0.0f") || code.contains("0f"));
  }

  @Test
  public void testVariableRefExprString() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float));
    String code = emitter.eval(new VariableRefExpr("$name"));
    assertTrue("Should generate getString", code.contains("getString(\"name\")"));
  }

  @Test
  public void testVariableRefExprBoolean() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float));
    String code = emitter.eval(new VariableRefExpr("$flag"));
    assertTrue("Should generate getBoolean", code.contains("getBoolean(\"flag\")"));
  }

  // ── ComparisonExpr ──

  @Test
  public void testComparisonExpr() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float));
    ComparisonExpr comp = new ComparisonExpr(leaf("$x"), ">", leaf("5"));
    String code = emitter.eval(comp);
    assertTrue("Should contain > operator", code.contains(">"));
    assertTrue("Should contain getNumber", code.contains("getNumber"));
    assertTrue("Should contain 5.0f", code.contains("5.0f"));
  }

  // ── BooleanOrExpr ──

  @Test
  public void testBooleanExprLiteral() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float));
    String code = emitter.eval(boolWrap("true"));
    assertEquals("true", code);
  }

  // ── IfExpr ──

  @Test
  public void testIfExpr() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    BooleanOrExpr condition = boolWrap("true");
    ExpressionExpr thenExpr = new ExpressionExpr(leaf("100"));
    ExpressionExpr elseExpr = new ExpressionExpr(leaf("200"));
    IfExpr ifExpr = new IfExpr(condition, thenExpr, elseExpr);
    String code = emitter.eval(ifExpr);
    assertTrue("Should be ternary", code.contains("?"));
    assertTrue("Should contain 100", code.contains("100"));
    assertTrue("Should contain 200", code.contains("200"));
  }

  // ── StringConcatExpr ──

  @Test
  public void testStringConcatExprLiteral() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float));
    String code = emitter.eval(new StringConcatExpr("hello", List.of(), List.of()));
    assertEquals("\"hello\"", code);
  }

  // ── NumberMatchExpr ──

  @Test
  public void testNumberMatchExpr() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));

    NumberCaseExpr case1 = new NumberCaseExpr(
        boolWrap("true"),
        new NumberCaseValueExpr(leaf("100")));
    NumberDefaultCaseExpr defaultCase = new NumberDefaultCaseExpr(new NumberCaseValueExpr(leaf("300")));

    NumberMatchExpr matchExpr = new NumberMatchExpr(case1, List.of(), defaultCase);
    String code = emitter.eval(matchExpr);
    assertTrue("Should contain ternary", code.contains("?"));
    assertTrue("Should contain 100", code.contains("100"));
    assertTrue("Should contain 300", code.contains("300"));
  }

  // ── buildJavaClass ──

  @Test
  public void testBuildJavaClass() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));
    String javaCode = emitter.buildJavaClass("TestCalc", "3.0f+4.0f");
    assertTrue("Should contain class name", javaCode.contains("class TestCalc"));
    assertTrue("Should contain expression", javaCode.contains("3.0f+4.0f"));
    assertTrue("Should contain evaluate method", javaCode.contains("evaluate("));
    assertTrue("Should have return type", javaCode.contains("Float") || javaCode.contains("float"));
  }

  // ── Int type ──

  @Test
  public void testIntType() {
    P4TypedJavaCodeEmitter emitter = new P4TypedJavaCodeEmitter(
        new SpecifiedExpressionTypes(ExpressionTypes._int, ExpressionTypes._int));
    BinaryExpr add = binary(leaf("3"), "+", leaf("4"));
    String code = emitter.eval(add);
    assertEquals("(3+4)", code);
  }
}
