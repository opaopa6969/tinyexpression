package org.unlaxer.tinyexpression.dap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Map;

import org.junit.Test;

public class TinyExpressionDapRuntimeBridgeTest {

  @Test
  public void testDebugVariablesReflectRequestedRuntimeBackend() {
    Map<String, String> dslJava = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "dsl-javacode");
    assertEquals("true", dslJava.get("bridgeAttached"));
    assertEquals("DSL_JAVA_CODE", dslJava.get("selectedExecutionBackend"));
    assertEquals("DSL_JAVA_CODE", dslJava.get("_tinyExecutionBackend"));

    Map<String, String> dslJavaAlias = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "dsl_java_code");
    assertEquals("DSL_JAVA_CODE", dslJavaAlias.get("selectedExecutionBackend"));

    Map<String, String> ast = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "ast");
    assertEquals("AST_EVALUATOR", ast.get("selectedExecutionBackend"));
    assertEquals("AST_EVALUATOR", ast.get("_tinyExecutionBackend"));
    assertNotNull(ast.get("evaluationResultType"));
    assertNotNull(ast.get("evaluationResultNormalized"));

    Map<String, String> token = TinyExpressionDapRuntimeBridge.debugVariables("1+1", "token");
    assertEquals("JAVA_CODE", token.get("selectedExecutionBackend"));
    assertEquals("JAVA_CODE", token.get("_tinyExecutionBackend"));
    assertNotNull(token.get("evaluationResultType"));
    assertNotNull(token.get("evaluationResultNormalized"));
  }
}
