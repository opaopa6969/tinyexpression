package org.unlaxer.tinyexpression.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.DslJavaCodeCalculator;
import org.unlaxer.tinyexpression.evaluator.javacode.legacy.LegacyAstCreatorJavaCodeCalculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class FormulaInfoExecutionBackendSelectionTest {

  @Test
  public void testExecutionBackendMetadataAliasSelectsDslJavaCode() {
    FormulaInfoAdditionalFields additionalFields = additionalFields();
    String text = formulaInfo(
        "backend:dsl-javacode",
        "dslFromMetadata");
    FormulaInfo formulaInfo = parseSingle(text, additionalFields);

    assertEquals(ExecutionBackend.DSL_JAVA_CODE.name(), formulaInfo.executionBackend);
    assertTrue(formulaInfo.calculator() instanceof DslJavaCodeCalculator);
    assertEquals("DSL_JAVA_CODE", formulaInfo.calculator().getObject("_tinyExecutionBackend", String.class));
    assertEquals("dsl-javacode", formulaInfo.calculator().getObject("_tinyExecutionMode", String.class));
    assertEquals("legacy-javacode-bridge",
        formulaInfo.calculator().getObject("_tinyExecutionImplementation", String.class));
    assertEquals(Boolean.TRUE,
        formulaInfo.calculator().getObject("_tinyExecutionBridgeImplementation", Boolean.class));
    assertEquals(Boolean.FALSE,
        formulaInfo.calculator().getObject("_tinyExecutionNonBridgeImplementation", Boolean.class));
  }

  @Test
  public void testExecutionBackendMetadataSelectsDslJavaCodeNativeEmitterForLiteral() {
    FormulaInfoAdditionalFields additionalFields = additionalFields();
    StringBuilder builder = new StringBuilder();
    builder
        .append("backend:dsl-javacode").append('\n')
        .append("calculatorName:dslLiteralNative").append('\n')
        .append("resultType:float").append('\n')
        .append("formula:").append('\n')
        .append("1").append('\n')
        .append("---END_OF_PART---").append('\n');

    FormulaInfo formulaInfo = parseSingle(builder.toString(), additionalFields);

    assertEquals(ExecutionBackend.DSL_JAVA_CODE.name(), formulaInfo.executionBackend);
    assertTrue(formulaInfo.calculator() instanceof DslJavaCodeCalculator);
    assertEquals("dsl-javacode-native",
        formulaInfo.calculator().getObject("_tinyExecutionImplementation", String.class));
    assertEquals(Boolean.FALSE,
        formulaInfo.calculator().getObject("_tinyExecutionBridgeImplementation", Boolean.class));
    assertEquals(Boolean.TRUE,
        formulaInfo.calculator().getObject("_tinyExecutionNonBridgeImplementation", Boolean.class));
    assertEquals("native-generated-ast",
        formulaInfo.calculator().getObject("_tinyDslJavaEmitterMode", String.class));
    assertEquals(Boolean.TRUE,
        formulaInfo.calculator().getObject("_tinyDslJavaNativeEmitterUsed", Boolean.class));
  }

  @Test
  public void testExecutionBackendFallsBackToConfiguredDefaultWhenMetadataMissing() {
    FormulaInfoAdditionalFields additionalFields = additionalFields()
        .setExecutionBackend(ExecutionBackend.AST_EVALUATOR);
    String text = formulaInfo(
        null,
        "astFromConfigDefault");
    FormulaInfo formulaInfo = parseSingle(text, additionalFields);

    assertEquals(ExecutionBackend.AST_EVALUATOR.name(), formulaInfo.executionBackend);
    assertEquals("AST_EVALUATOR", formulaInfo.calculator().getObject("_tinyExecutionBackend", String.class));
    assertEquals("ast-evaluator", formulaInfo.calculator().getObject("_tinyExecutionMode", String.class));
    assertEquals(Boolean.FALSE,
        formulaInfo.calculator().getObject("_tinyExecutionBridgeImplementation", Boolean.class));
    assertEquals(Boolean.TRUE,
        formulaInfo.calculator().getObject("_tinyExecutionNonBridgeImplementation", Boolean.class));
  }

  @Test
  public void testExecutionBackendMetadataOverridesConfiguredDefault() {
    FormulaInfoAdditionalFields additionalFields = additionalFields()
        .setExecutionBackend(ExecutionBackend.DSL_JAVA_CODE);
    String text = formulaInfo(
        "executionBackend:JAVA_CODE",
        "javaFromMetadata");
    FormulaInfo formulaInfo = parseSingle(text, additionalFields);

    assertEquals(ExecutionBackend.JAVA_CODE.name(), formulaInfo.executionBackend);
    assertEquals("JAVA_CODE", formulaInfo.calculator().getObject("_tinyExecutionBackend", String.class));
    assertEquals("javacode", formulaInfo.calculator().getObject("_tinyExecutionMode", String.class));
    assertEquals(Boolean.FALSE,
        formulaInfo.calculator().getObject("_tinyExecutionBridgeImplementation", Boolean.class));
    assertEquals(Boolean.TRUE,
        formulaInfo.calculator().getObject("_tinyExecutionNonBridgeImplementation", Boolean.class));
  }

  @Test
  public void testExecutionBackendMetadataSelectsLegacyAstCreatorJavaCode() {
    FormulaInfoAdditionalFields additionalFields = additionalFields();
    String text = formulaInfo(
        "backend:legacy-astcreator",
        "legacyAstCreatorFromMetadata");
    FormulaInfo formulaInfo = parseSingle(text, additionalFields);

    assertEquals(ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR.name(), formulaInfo.executionBackend);
    assertTrue(formulaInfo.calculator() instanceof LegacyAstCreatorJavaCodeCalculator);
    assertEquals("JAVA_CODE_LEGACY_ASTCREATOR",
        formulaInfo.calculator().getObject("_tinyExecutionBackend", String.class));
    assertEquals("legacy-astcreator",
        formulaInfo.calculator().getObject("_tinyExecutionMode", String.class));
    assertEquals("legacy-javacode-astcreator",
        formulaInfo.calculator().getObject("_tinyExecutionImplementation", String.class));
  }

  @Test
  public void testInvalidExecutionBackendMetadataFailsFast() {
    FormulaInfoAdditionalFields additionalFields = additionalFields();
    String text = formulaInfo(
        "executionBackend:not-a-backend",
        "invalidBackend");

    assertThrows(RuntimeException.class, () -> parseSingle(text, additionalFields));
  }

  private FormulaInfo parseSingle(String text, FormulaInfoAdditionalFields additionalFields) {
    FormulaInfoList formulaInfoList = FormulaInfoList.parse(
        text,
        additionalFields,
        Thread.currentThread().getContextClassLoader()).get();
    return formulaInfoList.get().get(0);
  }

  private FormulaInfoAdditionalFields additionalFields() {
    return new FormulaInfoAdditionalFields("siteId",
        formulaInfo -> {
          String checkKind = formulaInfo.extraValueByKey.get("checkKind");
          return checkKind != null ? checkKind : formulaInfo.calculatorName;
        });
  }

  private String formulaInfo(String executionBackendLine, String calculatorName) {
    StringBuilder builder = new StringBuilder();
    if (executionBackendLine != null) {
      builder.append(executionBackendLine).append('\n');
    }
    builder
        .append("calculatorName:").append(calculatorName).append('\n')
        .append("resultType:float").append('\n')
        .append("formula:").append('\n')
        .append("1+1").append('\n')
        .append("---END_OF_PART---").append('\n');
    return builder.toString();
  }
}
