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
    // P4-typed emitter now handles 1+1 natively (no bridge needed)
    String impl = dslJava.get("_tinyExecutionImplementation");
    assertTrue("impl=" + impl,
        "dsl-javacode-native".equals(impl) || "legacy-javacode-bridge".equals(impl));

    Map<String, String> dslJavaLiteral = TinyExpressionDapRuntimeBridge.debugVariables("1", "dsl-javacode");
    assertEquals("dsl-javacode-native", dslJavaLiteral.get("_tinyExecutionImplementation"));
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
  }
}
