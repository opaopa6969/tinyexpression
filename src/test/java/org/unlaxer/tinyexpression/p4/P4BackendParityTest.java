package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * Parity tests for P4 execution backends.
 * <p>
 * Verifies that:
 * <ol>
 *   <li>{@code P4_AST_EVALUATOR} and {@code P4_DSL_JAVA_CODE} produce the same evaluation
 *       results as the reference {@code JAVA_CODE} backend.</li>
 *   <li>{@code _tinyP4ParserUsed=true} is set for formulas the P4 grammar can parse.</li>
 *   <li>{@code _tinyP4ParserUsed=false} is set (with graceful fallback) for formulas that
 *       use tinyexpression syntax not yet covered by the P4 grammar.</li>
 * </ol>
 */
public class P4BackendParityTest {

  // ── Formulas parseable by the P4 grammar ──────────────────────────────────

  /**
   * Simple arithmetic formulas — P4 grammar handles NumberExpression fully.
   * (10-2)*(7-3) is now included: the nested-parenthesis multiplication bug in
   * DSL_JAVA_CODE / P4_DSL_JAVA_CODE was fixed in v1.4.11 via P4TypedJavaCodeEmitter.
   */
  private static final List<String> P4_PARSEABLE_NUMBER_FORMULAS = List.of(
      "1",
      "42",
      "1+2",
      "3*4-5",
      "1+2*3",
      "100/4",
      "7-3+1",
      "(10-2)*(7-3)"
  );

  /** Boolean literals — P4 grammar handles 'true' / 'false'. */
  private static final List<String> P4_PARSEABLE_BOOLEAN_FORMULAS = List.of(
      "true",
      "false"
  );

  /** Match expression formulas — P4 has NumberMatchExpression / StringMatchExpression. */
  private static final List<String> P4_PARSEABLE_MATCH_FORMULAS = List.of(
      "match{1==1->1,default->0}",
      "match{1==0->1,default->2}",
      "match{2>1->10,default->20}"
  );

  // ── Formulas NOT parseable by P4 (use old tinyexpression syntax) ──────────

  /**
   * Formulas that P4 grammar cannot parse but that the tinyexpression JAVA_CODE backend
   * evaluates correctly.
   * Note: sin(30) and max(3,7) were moved to P4_PARSEABLE_NUMBER_FORMULAS after
   * Math functions were added to the P4 grammar.
   * Currently empty — all float-returning formulas are now parseable by P4.
   */
  private static final List<String> P4_UNPARSEABLE_BUT_VALID_FORMULAS = List.of();

  // =========================================================================
  // P6-2-a: All 6 backends agree on number arithmetic
  // =========================================================================

  @Test
  public void testSixBackendParityOnNumberArithmetic() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    for (int i = 0; i < P4_PARSEABLE_NUMBER_FORMULAS.size(); i++) {
      String formula = P4_PARSEABLE_NUMBER_FORMULAS.get(i);

      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator()
          .create(new Source(formula), "Parity6_jc_" + i, types, cl);
      Calculator legacy = CalculatorCreatorRegistry.legacyAstCreatorJavaCodeCreator()
          .create(new Source(formula), "Parity6_leg_" + i, types, cl);
      Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator()
          .create(new Source(formula), "Parity6_ast_" + i, types, cl);
      Calculator dsl = CalculatorCreatorRegistry.dslJavaCodeCreator()
          .create(new Source(formula), "Parity6_dsl_" + i, types, cl);
      Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
          .create(new Source(formula), "Parity6_p4ast_" + i, types, cl);
      Calculator p4Dsl = CalculatorCreatorRegistry.p4DslJavaCodeCreator()
          .create(new Source(formula), "Parity6_p4dsl_" + i, types, cl);

      CalculationContext ctx = CalculationContext.newConcurrentContext();

      float ref = floatValue(javaCode.apply(ctx));
      assertEquals("legacy parity, formula=" + formula, ref, floatValue(legacy.apply(ctx)), 0.001f);
      assertEquals("ast parity, formula=" + formula, ref, floatValue(ast.apply(ctx)), 0.001f);
      assertEquals("dsl parity, formula=" + formula, ref, floatValue(dsl.apply(ctx)), 0.001f);
      assertEquals("p4-ast parity, formula=" + formula, ref, floatValue(p4Ast.apply(ctx)), 0.001f);
      assertEquals("p4-dsl parity, formula=" + formula, ref, floatValue(p4Dsl.apply(ctx)), 0.001f);
    }
  }

  // =========================================================================
  // P6-2-b: P4 parser used marker is true for P4-parseable formulas
  // =========================================================================

  @Test
  public void testP4ParserUsedTrueForP4ParseableArithmetic() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    for (int i = 0; i < P4_PARSEABLE_NUMBER_FORMULAS.size(); i++) {
      String formula = P4_PARSEABLE_NUMBER_FORMULAS.get(i);
      Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
          .create(new Source(formula), "P4Used_" + i, types, cl);

      // _tinyP4ParserUsed is available before apply() via getObject() override
      Boolean used = p4Ast.getObject("_tinyP4ParserUsed", Boolean.class);
      assertNotNull("_tinyP4ParserUsed should be set for formula=" + formula, used);
      assertTrue("_tinyP4ParserUsed should be true for P4-parseable formula=" + formula, used);

      String nodeType = p4Ast.getObject("_tinyP4AstNodeType", String.class);
      assertNotNull("_tinyP4AstNodeType should be set", nodeType);
      assertFalse("_tinyP4AstNodeType should not be 'parse-failed'",
          "parse-failed".equals(nodeType));
    }
  }

  @Test
  public void testP4ParserUsedTrueForMatchFormulas() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    for (int i = 0; i < P4_PARSEABLE_MATCH_FORMULAS.size(); i++) {
      String formula = P4_PARSEABLE_MATCH_FORMULAS.get(i);
      Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
          .create(new Source(formula), "P4Match_" + i, types, cl);

      Boolean used = p4Ast.getObject("_tinyP4ParserUsed", Boolean.class);
      assertNotNull("_tinyP4ParserUsed should be set for match formula=" + formula, used);
      assertTrue("_tinyP4ParserUsed should be true for match formula=" + formula, used);
    }
  }

  @Test
  public void testP4ExactParseRejectsAmbiguousDirectMatchVariableWithoutHint() {
    String formula = "match{1==1->$val,default->0}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "MatchStrictRef_jc", types, cl);
    Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
        .create(new Source(formula), "MatchStrictRef_p4ast", types, cl);

    assertEquals(Boolean.FALSE, p4Ast.getObject("_tinyP4ParserUsed", Boolean.class));
    assertEquals(Boolean.FALSE, p4Ast.getObject("_tinyP4ParserExact", Boolean.class));
    assertEquals("semantic", p4Ast.getObject("_tinyP4ParserProbeMode", String.class));

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    ctx.set("val", 10f);
    float ref = floatValue(javaCode.apply(ctx));
    float actual = floatValue(p4Ast.apply(ctx));
    assertEquals("semantic rejection should still preserve legacy result", ref, actual, 0.001f);
    assertFalse("strict-typing rejection should avoid p4-typed runtime",
        "p4-typed".equals(p4Ast.getObject("_astEvaluatorRuntime", String.class)));
  }

  @Test
  public void testP4ExactParseAcceptsHintedDirectMatchVariable() {
    String formula = "match{1==1->$val as number,default->0}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
        .create(new Source(formula), "MatchStrictHint_p4ast", types, cl);

    assertEquals(Boolean.TRUE, p4Ast.getObject("_tinyP4ParserUsed", Boolean.class));
    assertFalse("hinted direct variable should not be semantically rejected",
        "semantic".equals(p4Ast.getObject("_tinyP4ParserProbeMode", String.class)));
  }

  @Test
  public void testP4ExactParseRejectsDirectMatchMethodInvocation() {
    String formula = "match{1==1->internal score(),default->0}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
        .create(new Source(formula), "MatchStrictMethod_p4ast", types, cl);

    assertEquals(Boolean.FALSE, p4Ast.getObject("_tinyP4ParserUsed", Boolean.class));
    assertEquals(Boolean.FALSE, p4Ast.getObject("_tinyP4ParserExact", Boolean.class));
    assertEquals("semantic", p4Ast.getObject("_tinyP4ParserProbeMode", String.class));
  }

  // =========================================================================
  // P6-2-c: P4 backends fall back gracefully for non-P4 syntax
  // =========================================================================

  @Test
  public void testP4FallbackGracefulForOldSyntax() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    for (int i = 0; i < P4_UNPARSEABLE_BUT_VALID_FORMULAS.size(); i++) {
      String formula = P4_UNPARSEABLE_BUT_VALID_FORMULAS.get(i);
      Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
          .create(new Source(formula), "P4Fallback_" + i, types, cl);

      // P4 parser should not have parsed it
      Boolean used = p4Ast.getObject("_tinyP4ParserUsed", Boolean.class);
      assertNotNull("_tinyP4ParserUsed should be set even for unparseable formula=" + formula, used);
      assertFalse("_tinyP4ParserUsed should be false for old-syntax formula=" + formula, used);

      // But evaluation should still work via AST evaluator fallback
      Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator()
          .create(new Source(formula), "P4FallbackRef_" + i, types, cl);
      CalculationContext ctx = CalculationContext.newConcurrentContext();
      float ref = floatValue(javaCode.apply(ctx));
      float p4Val = floatValue(p4Ast.apply(ctx));
      assertEquals("fallback parity, formula=" + formula, ref, p4Val, 0.001f);
    }
  }

  // =========================================================================
  // P6-2-c-2: All 6 backends agree on nested-parenthesis multiplication — v1.4.11 fix
  // =========================================================================

  @Test
  public void testAllSixBackendsParityOnParenthesizedArithmetic() {
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    String formula = "(10-2)*(7-3)";
    Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "Paren3_jc", types, cl);
    Calculator legacy = CalculatorCreatorRegistry.legacyAstCreatorJavaCodeCreator()
        .create(new Source(formula), "Paren3_leg", types, cl);
    Calculator ast = CalculatorCreatorRegistry.astEvaluatorCreator()
        .create(new Source(formula), "Paren3_ast", types, cl);
    Calculator dsl = CalculatorCreatorRegistry.dslJavaCodeCreator()
        .create(new Source(formula), "Paren3_dsl", types, cl);
    Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
        .create(new Source(formula), "Paren3_p4ast", types, cl);
    Calculator p4Dsl = CalculatorCreatorRegistry.p4DslJavaCodeCreator()
        .create(new Source(formula), "Paren3_p4dsl", types, cl);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    float ref = floatValue(javaCode.apply(ctx));
    assertEquals(32f, ref, 0.001f);
    assertEquals("legacy parity, formula=" + formula, ref, floatValue(legacy.apply(ctx)), 0.001f);
    assertEquals("ast parity, formula=" + formula, ref, floatValue(ast.apply(ctx)), 0.001f);
    assertEquals("dsl parity, formula=" + formula, ref, floatValue(dsl.apply(ctx)), 0.001f);
    assertEquals("p4-ast parity, formula=" + formula, ref, floatValue(p4Ast.apply(ctx)), 0.001f);
    assertEquals("p4-dsl parity, formula=" + formula, ref, floatValue(p4Dsl.apply(ctx)), 0.001f);
  }

  // =========================================================================
  // P6-2-d: ExecutionBackend enum aliases resolve correctly
  // =========================================================================

  @Test
  public void testExecutionBackendAliasResolution() {
    assertEquals(ExecutionBackend.P4_AST_EVALUATOR,
        ExecutionBackend.fromRuntimeMode("p4-ast").orElse(null));
    assertEquals(ExecutionBackend.P4_AST_EVALUATOR,
        ExecutionBackend.fromRuntimeMode("p4-ast-evaluator").orElse(null));
    assertEquals(ExecutionBackend.P4_DSL_JAVA_CODE,
        ExecutionBackend.fromRuntimeMode("p4-dsl-javacode").orElse(null));
    assertEquals(ExecutionBackend.P4_DSL_JAVA_CODE,
        ExecutionBackend.fromRuntimeMode("p4-dsl-java-code").orElse(null));
    assertEquals("p4-ast", ExecutionBackend.P4_AST_EVALUATOR.runtimeModeMarker());
    assertEquals("p4-dsl-javacode", ExecutionBackend.P4_DSL_JAVA_CODE.runtimeModeMarker());
  }

  // =========================================================================
  // P6-2-e: match expressions parity across all 6 backends
  // =========================================================================

  @Test
  public void testMatchExpressionParityAcrossSixBackends() {
    String formula = "match{1==1->99,default->0}";
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    Calculator javaCode = CalculatorCreatorRegistry.javaCodeCreator()
        .create(new Source(formula), "MatchParity_jc", types, cl);
    Calculator p4Ast = CalculatorCreatorRegistry.p4AstEvaluatorCreator()
        .create(new Source(formula), "MatchParity_p4ast", types, cl);
    Calculator p4Dsl = CalculatorCreatorRegistry.p4DslJavaCodeCreator()
        .create(new Source(formula), "MatchParity_p4dsl", types, cl);

    CalculationContext ctx = CalculationContext.newConcurrentContext();
    float ref = floatValue(javaCode.apply(ctx));
    assertEquals(99f, ref, 0.001f);
    assertEquals("p4-ast match parity", ref, floatValue(p4Ast.apply(ctx)), 0.001f);
    assertEquals("p4-dsl match parity", ref, floatValue(p4Dsl.apply(ctx)), 0.001f);

    // Match formula is P4-parseable
    Boolean p4Used = p4Ast.getObject("_tinyP4ParserUsed", Boolean.class);
    assertTrue("P4 should parse match expression", p4Used);
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  private static float floatValue(Object result) {
    if (result instanceof Number n) return n.floatValue();
    if (result instanceof String s) {
      try { return new BigDecimal(s).floatValue(); } catch (NumberFormatException ignored) {}
    }
    throw new AssertionError("Cannot convert to float: " + result + " (" +
        (result == null ? "null" : result.getClass().getName()) + ")");
  }
}
