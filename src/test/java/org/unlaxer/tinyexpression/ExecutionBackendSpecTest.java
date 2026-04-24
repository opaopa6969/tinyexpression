package org.unlaxer.tinyexpression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * Spec coverage for ExecutionBackend: runtimeImplementationMarker(),
 * bridgeImplementation(), parse(), and fromRuntimeMode() aliases not covered
 * by existing P4BackendParityTest.
 */
public class ExecutionBackendSpecTest {

    // -------------------------------------------------------------------------
    // runtimeImplementationMarker
    // -------------------------------------------------------------------------

    @Test
    public void runtimeImplementationMarker_javaCode() {
        assertEquals("legacy-javacode", ExecutionBackend.JAVA_CODE.runtimeImplementationMarker());
    }

    @Test
    public void runtimeImplementationMarker_legacyAstCreator() {
        assertEquals("legacy-javacode-astcreator",
                ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR.runtimeImplementationMarker());
    }

    @Test
    public void runtimeImplementationMarker_astEvaluator() {
        assertEquals("ast-evaluator", ExecutionBackend.AST_EVALUATOR.runtimeImplementationMarker());
    }

    @Test
    public void runtimeImplementationMarker_dslJavaCode() {
        assertEquals("legacy-javacode-bridge", ExecutionBackend.DSL_JAVA_CODE.runtimeImplementationMarker());
    }

    @Test
    public void runtimeImplementationMarker_p4AstEvaluator() {
        assertEquals("p4-ast-evaluator", ExecutionBackend.P4_AST_EVALUATOR.runtimeImplementationMarker());
    }

    @Test
    public void runtimeImplementationMarker_p4DslJavaCode() {
        assertEquals("p4-dsl-javacode", ExecutionBackend.P4_DSL_JAVA_CODE.runtimeImplementationMarker());
    }

    // -------------------------------------------------------------------------
    // bridgeImplementation
    // -------------------------------------------------------------------------

    @Test
    public void bridgeImplementation_dslJavaCodeIsTrue() {
        assertTrue(ExecutionBackend.DSL_JAVA_CODE.bridgeImplementation());
    }

    @Test
    public void bridgeImplementation_p4DslJavaCodeIsTrue() {
        assertTrue(ExecutionBackend.P4_DSL_JAVA_CODE.bridgeImplementation());
    }

    @Test
    public void bridgeImplementation_javaCodeIsFalse() {
        assertFalse(ExecutionBackend.JAVA_CODE.bridgeImplementation());
    }

    @Test
    public void bridgeImplementation_astEvaluatorIsFalse() {
        assertFalse(ExecutionBackend.AST_EVALUATOR.bridgeImplementation());
    }

    @Test
    public void bridgeImplementation_p4AstEvaluatorIsFalse() {
        assertFalse(ExecutionBackend.P4_AST_EVALUATOR.bridgeImplementation());
    }

    // -------------------------------------------------------------------------
    // parse()
    // -------------------------------------------------------------------------

    @Test
    public void parse_exactEnumNameWorks() {
        assertEquals(Optional.of(ExecutionBackend.JAVA_CODE),
                ExecutionBackend.parse("JAVA_CODE"));
        assertEquals(Optional.of(ExecutionBackend.AST_EVALUATOR),
                ExecutionBackend.parse("AST_EVALUATOR"));
        assertEquals(Optional.of(ExecutionBackend.P4_AST_EVALUATOR),
                ExecutionBackend.parse("P4_AST_EVALUATOR"));
    }

    @Test
    public void parse_hyphenatedAliasForDslJavaCode() {
        assertEquals(Optional.of(ExecutionBackend.DSL_JAVA_CODE),
                ExecutionBackend.parse("dsl-javacode"));
        assertEquals(Optional.of(ExecutionBackend.DSL_JAVA_CODE),
                ExecutionBackend.parse("DSL-JAVA-CODE"));
    }

    @Test
    public void parse_compactAlias() {
        assertEquals(Optional.of(ExecutionBackend.JAVA_CODE),
                ExecutionBackend.parse("JAVACODE"));
        assertEquals(Optional.of(ExecutionBackend.P4_DSL_JAVA_CODE),
                ExecutionBackend.parse("P4DSLJAVACODE"));
    }

    @Test
    public void parse_nullReturnsEmpty() {
        assertEquals(Optional.empty(), ExecutionBackend.parse(null));
    }

    @Test
    public void parse_emptyStringReturnsEmpty() {
        assertEquals(Optional.empty(), ExecutionBackend.parse(""));
        assertEquals(Optional.empty(), ExecutionBackend.parse("  "));
    }

    @Test
    public void parse_unknownValueReturnsEmpty() {
        assertEquals(Optional.empty(), ExecutionBackend.parse("NOT_A_BACKEND"));
    }

    // -------------------------------------------------------------------------
    // fromRuntimeMode() – legacy aliases (token, ast, etc.)
    // -------------------------------------------------------------------------

    @Test
    public void fromRuntimeMode_tokenAliasIsJavaCode() {
        assertEquals(Optional.of(ExecutionBackend.JAVA_CODE),
                ExecutionBackend.fromRuntimeMode("token"));
    }

    @Test
    public void fromRuntimeMode_astAliasIsAstEvaluator() {
        assertEquals(Optional.of(ExecutionBackend.AST_EVALUATOR),
                ExecutionBackend.fromRuntimeMode("ast"));
    }

    @Test
    public void fromRuntimeMode_ootcLegacyAliasIsLegacyAstCreator() {
        assertEquals(Optional.of(ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR),
                ExecutionBackend.fromRuntimeMode("ootc-legacy"));
        assertEquals(Optional.of(ExecutionBackend.JAVA_CODE_LEGACY_ASTCREATOR),
                ExecutionBackend.fromRuntimeMode("legacy-astcreator"));
    }

    @Test
    public void fromRuntimeMode_nullOrBlankReturnsEmpty() {
        assertEquals(Optional.empty(), ExecutionBackend.fromRuntimeMode(null));
        assertEquals(Optional.empty(), ExecutionBackend.fromRuntimeMode(""));
        assertEquals(Optional.empty(), ExecutionBackend.fromRuntimeMode("   "));
    }

    @Test
    public void fromRuntimeMode_unknownReturnsEmpty() {
        assertEquals(Optional.empty(), ExecutionBackend.fromRuntimeMode("unknown-backend"));
    }
}
