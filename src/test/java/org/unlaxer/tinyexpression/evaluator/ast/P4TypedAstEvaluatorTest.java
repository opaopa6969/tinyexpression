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
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SliceSourceSupport;
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

  @Test
  public void testBinaryArithmeticWithStructuredNumberLeaf() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), newContext());
    BinaryExpr addSin = binary(leaf("1"), "+", leaf("sin(30)"));
    BinaryExpr addMax = binary(leaf("1"), "+", leaf("max(3,7)"));
    assertEquals(1.5f, ((Number) evaluator.eval(addSin)).floatValue(), 0.001f);
    assertEquals(8.0f, ((Number) evaluator.eval(addMax)).floatValue(), 0.001f);
  }

  @Test
  public void testMixedArithmeticWithNestedMathFunctionFromParsedAst() {
    String formula = "(1+1)/3+sin(30)";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.1666667f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testMixedArithmeticWithLeadingMathFunctionFromParsedAst() {
    String formula = "sin(30)*2";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithLenFunctionFromParsedAst() {
    String formula = "if(len(\"AlmondChocolate\")==15){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithInMethodFromParsedAst() {
    String formula = "if('cnjpuszn'[4:6].in('en','ca','us')){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithVariableInMethodFromParsedAst() {
    CalculationContext context = newContext();
    context.set("country", "jp");
    String formula = "if($country.in('ca','jp','us')){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        context,
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithInDayTimeRangeFromParsedAst() {
    CalculationContext context = newContext();
    context.set("nowDayOfWeek", 3f);
    context.set("nowHour", 12f);
    String formula = "if(inDayTimeRange(MONDAY,10,FRIDAY,23)==true){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        context,
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithNestedSliceFromParsedAst() {
    String formula = "if('gateman'[::-1][0:4]=='name'){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testBlockCommentedIfExprFromParsedAst() {
    String formula = "if(10==20 /*test*/) /*test*/{ /*test*/ 10/*test*/ }/*test*/ else/*test*/ {/*test*/ 0/*test*/}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(0.0f, ((Number) result).floatValue(), 0.001f);
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

  @Test
  public void testVariableRefExprWithEmptyMappedNameUsesSourceSnippet() {
    CalculationContext context = newContext();
    context.setObject("payload", "ctx-object");
    String formula = "$payload";
    VariableRefExpr mapped = (VariableRefExpr) TinyExpressionP4Mapper.parse(formula, "VariableRefExpr");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        context,
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(mapped);
    assertEquals("ctx-object", result);
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

  @Test
  public void testParsedIfExprWithMathFunctionComparisonUsesSourceAwareChildren() {
    String formula = "if(max(-1,-2)==-1){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testParsedIfExprWithMixedBooleanConditionUsesSourceAwareChildren() {
    String formula = "if(false|false|false|(true&true)){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testParsedIfExprWithBooleanEqualityUsesSourceAwareChildren() {
    String formula = "if(true!=false){1}else{0}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testStandaloneIsPresentUsesSourceAwareVariableName() {
    CalculationContext context = newContext();
    context.set("presentName", "hello");
    String formula = "isPresent($presentName)";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._boolean).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float),
        context,
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(true, result);
  }

  @Test
  public void testParsedIfExprWithNestedIfElseBranchUsesSourceAwareChildren() {
    String formula = "if(not(not(0.7*5>5))){6*8}else{0.3*if(1>0){0.3}else{0.2}}";
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        formula,
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(0.09f, ((Number) result).floatValue(), 0.001f);
  }

  // ── StringConcatExpr ──

  @Test
  public void testStringConcatExprLiteral() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object result = evaluator.eval(new StringConcatExpr("hello world", List.of(), List.of()));
    assertEquals("hello world", result);
  }

  @Test
  public void testStringConcatExprStructuredLeaf() {
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), newContext());
    Object trimResult = evaluator.eval(new StringConcatExpr("trim(' opa 133 ')", List.of(), List.of()));
    assertEquals("opa 133", trimResult);

    Object groupedConcatResult = evaluator.eval(
        new StringConcatExpr("(\"opa\"+\"opa\"+\"6969\")", List.of(), List.of()));
    assertEquals("opaopa6969", groupedConcatResult);

    Object reverseSliceResult = evaluator.eval(
        new StringConcatExpr("'gateman'[::-1]", List.of(), List.of()));
    assertEquals("nametag", reverseSliceResult);

    Object steppedSliceResult = evaluator.eval(
        new StringConcatExpr("'1a2b3'[::2]", List.of(), List.of()));
    assertEquals("123", steppedSliceResult);
  }

  @Test
  public void testIfExprWithGroupedStringComparison() {
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(
        "if((\"opa\"+\"opa\"+\"6969\")==\"opaopa6969\"){1}else{0}",
        ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        "if((\"opa\"+\"opa\"+\"6969\")==\"opaopa6969\"){1}else{0}",
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithReverseSliceStringComparison() {
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(
        "if('gateman'[::-1]=='nametag'){1}else{0}",
        ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        "if('gateman'[::-1]=='nametag'){1}else{0}",
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testIfExprWithStepOnlySliceStringComparison() {
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(
        "if('1a2b3'[::2]=='123'){1}else{0}",
        ExpressionTypes._float).ast();
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        newContext(),
        "if('1a2b3'[::2]=='123'){1}else{0}",
        Thread.currentThread().getContextClassLoader());
    Object result = evaluator.eval(ast);
    assertEquals(1.0f, ((Number) result).floatValue(), 0.001f);
  }

  @Test
  public void testSliceSourcePartsOfNode() {
    TinyExpressionP4AST ast = P4PreferredAstMapper.parseDetailed(
        "'gateman'[::-1]",
        ExpressionTypes.string).ast();
    assertTrue(ast instanceof SliceExpr);
    P4SliceSourceSupport.SliceParts parts =
        P4SliceSourceSupport.slicePartsOfNode(ast, "'gateman'[::-1]").orElseThrow();
    assertEquals("'gateman'", parts.valueSource());
    assertNull(parts.startSource());
    assertNull(parts.endSource());
    assertEquals("-1", parts.stepSource());

    TinyExpressionP4AST steppedAst = P4PreferredAstMapper.parseDetailed(
        "'1a2b3'[::2]",
        ExpressionTypes.string).ast();
    assertTrue(steppedAst instanceof SliceExpr);
    P4SliceSourceSupport.SliceParts steppedParts =
        P4SliceSourceSupport.slicePartsOfNode(steppedAst, "'1a2b3'[::2]").orElseThrow();
    assertEquals("'1a2b3'", steppedParts.valueSource());
    assertNull(steppedParts.startSource());
    assertNull(steppedParts.endSource());
    assertEquals("2", steppedParts.stepSource());
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

  // ── String slice (Python-style) ──

  @Test
  public void testSliceStartEnd() {
    // $s[0:3] on "hello" → "hel"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", leaf("0"), leaf("3"), null));
    assertEquals("hel", result);
  }

  @Test
  public void testSliceStartOnly() {
    // $s[1:] on "hello" → "ello"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", leaf("1"), null, null));
    assertEquals("ello", result);
  }

  @Test
  public void testSliceEndOnly() {
    // $s[:3] on "hello" → "hel"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", null, leaf("3"), null));
    assertEquals("hel", result);
  }

  @Test
  public void testSliceWithStep() {
    // $s[::2] on "hello" → "hlo"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", null, null, leaf("2")));
    assertEquals("hlo", result);
  }

  @Test
  public void testSliceReverse() {
    // $s[::-1] on "hello" → "olleh"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", null, null, leaf("-1")));
    assertEquals("olleh", result);
  }

  @Test
  public void testSliceNegativeIndices() {
    // $s[-3:] on "hello" → "llo"
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", leaf("-3"), null, null));
    assertEquals("llo", result);
  }

  @Test
  public void testSliceEmptyResult() {
    // $s[3:3] on "hello" → ""
    CalculationContext ctx = newContext();
    ctx.set("s", "hello");
    P4TypedAstEvaluator evaluator = new P4TypedAstEvaluator(
        new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), ctx);
    Object result = evaluator.eval(new SliceExpr(
        "$s", leaf("3"), leaf("3"), null));
    assertEquals("", result);
  }
}
