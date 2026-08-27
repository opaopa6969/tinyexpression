package org.unlaxer.tinyexpression.dap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;

public class TinyExpressionDapRuntimeBridgeTest {

  @Test
  public void testDebugVariablesReflectRequestedRuntimeBackend() {
    Map<String, String> dslJava = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "dsl-javacode");
    assertEquals("true", dslJava.get("bridgeAttached"));
    assertEquals("DSL_JAVA_CODE", dslJava.get("selectedExecutionBackend"));
    assertEquals("DSL_JAVA_CODE", dslJava.get("_tinyExecutionBackend"));
    assertEquals("p4-typed-emitter", dslJava.get("_tinyExecutionImplementation"));

    Map<String, String> dslJavaLiteral = TinyExpressionDapRuntimeBridge.debugVariables("1", "dsl-javacode");
    assertEquals("p4-typed-emitter", dslJavaLiteral.get("_tinyExecutionImplementation"));
    String emitterMode = dslJavaLiteral.get("_tinyDslJavaEmitterMode");
    assertTrue("emitterMode=" + emitterMode,
        "native-generated-ast".equals(emitterMode) || "p4-typed-emitter".equals(emitterMode));
    assertEquals("true", dslJavaLiteral.get("_tinyDslJavaNativeEmitterUsed"));

    Map<String, String> dslJavaAlias = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "dsl_java_code");
    assertEquals("DSL_JAVA_CODE", dslJavaAlias.get("selectedExecutionBackend"));

    Map<String, String> ast = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "ast");
    assertEquals("AST_EVALUATOR", ast.get("selectedExecutionBackend"));
    assertEquals("AST_EVALUATOR", ast.get("_tinyExecutionBackend"));
    assertNotNull(ast.get("evaluationResultType"));
    assertNotNull(ast.get("evaluationResultNormalized"));
    assertNotNull(ast.get("_astEvaluatorGeneratedEmbeddedBridgeUsed"));

    Map<String, String> legacyAstCreator = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "legacy-astcreator");
    assertEquals("JAVA_CODE_LEGACY_ASTCREATOR", legacyAstCreator.get("selectedExecutionBackend"));
    assertEquals("JAVA_CODE_LEGACY_ASTCREATOR", legacyAstCreator.get("_tinyExecutionBackend"));

    Map<String, String> token = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "token");
    assertEquals("JAVA_CODE", token.get("selectedExecutionBackend"));
    assertEquals("JAVA_CODE", token.get("_tinyExecutionBackend"));
    assertNotNull(token.get("evaluationResultType"));
    assertNotNull(token.get("evaluationResultNormalized"));
    assertEquals("true", token.get("parity.allBackendsEvaluated"));
    assertEquals("true", token.get("parity.equalAll"));
    assertNotNull(token.get("parity.JAVA_CODE.normalized"));
    assertNotNull(token.get("parity.JAVA_CODE_LEGACY_ASTCREATOR.normalized"));
    assertNotNull(token.get("parity.AST_EVALUATOR.normalized"));
    assertNotNull(token.get("parity.DSL_JAVA_CODE.normalized"));
    assertNotNull(token.get("parity.P4_AST_EVALUATOR.normalized"));
    assertNotNull(token.get("parity.P4_DSL_JAVA_CODE.normalized"));
    assertEquals(token.get("parity.equalAll"), token.get("parity.equalAllWithP4"));
  }

  @Test
  public void testInjectedVariablesReachSelectedBackendAndAllParityBackends() {
    Map<String, Object> variables = Map.of("score", 42);
    String formula = "var $score as number set if not exists 0 description='score';\n$score + 8";

    assertEquals("50", TinyExpressionDapRuntimeBridge.evaluateForDisplay(
        formula, "p4-ast", variables));

    Map<String, String> debug = TinyExpressionDapRuntimeBridge.debugVariables(
        formula, "p4-ast", variables);
    assertEquals("P4_AST_EVALUATOR", debug.get("selectedExecutionBackend"));
    assertEquals("50", debug.get("evaluationResultNormalized"));
    assertEquals(debug.toString(), "true", debug.get("parity.allBackendsEvaluated"));
    assertEquals(debug.toString(), "true", debug.get("parity.equalAll"));
    assertEquals("50", debug.get("parity.P4_AST_EVALUATOR.normalized"));
    assertEquals("50", debug.get("parity.P4_DSL_JAVA_CODE.normalized"));
  }

  @Test
  public void testMissingModeDefaultsToGeneratedBackendAndInvalidModeDoesNotFallback() {
    Map<String, String> defaulted = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "");
    assertEquals("P4_AST_EVALUATOR", defaulted.get("selectedExecutionBackend"));

    Map<String, String> invalid = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "unknown");
    assertEquals("UNSUPPORTED", invalid.get("selectedExecutionBackend"));
    assertTrue(invalid.get("bridgeError").contains("Unsupported runtimeMode"));
  }

  @Test
  public void testFormulaInfoDocumentExecutesDependenciesWithInjectedContext() {
    String document = """
        calculatorName:base
        resultType:float
        var:baseResult
        formula:
        5
        ---END_OF_PART---
        calculatorName:derived
        resultType:float
        executionBackend:P4_AST_EVALUATOR
        dependsOn:base
        formula:
        $baseResult + $input
        ---END_OF_PART---
        """;

    Map<String, String> debug = TinyExpressionDapRuntimeBridge.debugFormulaInfoVariables(
        document, "derived", Map.of("input", 10));

    assertEquals(debug.toString(), "true", debug.get("formulaInfoDocument"));
    assertEquals(debug.toString(), "2", debug.get("formulaInfo.formulaCount"));
    assertEquals(debug.toString(), "5", debug.get("formulaInfo.base.normalized"));
    assertEquals(debug.toString(), "15", debug.get("formulaInfo.derived.normalized"));
    assertEquals(debug.toString(), "15", debug.get("formulaInfo.selectedResultNormalized"));
    assertEquals(debug.toString(), "P4_AST_EVALUATOR", debug.get("formulaInfo.selectedBackend"));
  }
}
