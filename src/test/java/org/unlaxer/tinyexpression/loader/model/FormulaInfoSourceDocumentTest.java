package org.unlaxer.tinyexpression.loader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FormulaInfoSourceDocumentTest {

  @Test
  public void parsesMultipleSectionsWithoutCompilingCalculators() {
    String source = """
        calculatorName:base
        resultType:float
        formula:
        1 + 2
        ---END_OF_PART---
        calculatorName:derived
        backend:P4_AST_EVALUATOR
        dependsOn:base
        formula:
        # FormulaInfo comment retained as whitespace for source mapping
        $score + 8
        ---END_OF_PART---
        """;

    FormulaInfoSourceDocument document = FormulaInfoSourceDocument.parse(source);

    assertEquals(2, document.sections().size());
    FormulaInfoSourceDocument.Section base = document.section("base").orElseThrow();
    assertEquals("javacode", base.runtimeMode());
    assertEquals("1 + 2", base.formulaText());
    assertEquals(3, base.lineOffset());

    FormulaInfoSourceDocument.Section derived = document.section("derived").orElseThrow();
    assertEquals("p4-ast", derived.runtimeMode());
    assertEquals("$score + 8", derived.formulaText());
    assertEquals(9, derived.lineOffset());
    assertTrue(derived.debugSource().contains("$score + 8"));
    assertTrue(!derived.debugSource().contains("FormulaInfo comment"));
    assertEquals(
        source.substring(0, derived.sourceOffset()).chars().filter(ch -> ch == '\n').count(),
        derived.lineOffset());
  }

  @Test
  public void rejectsPartialOrMissingFormulaDocuments() {
    assertThrows(IllegalArgumentException.class,
        () -> FormulaInfoSourceDocument.parse("calculatorName:x\n"));

    FormulaInfoSourceDocument invalidBackend = FormulaInfoSourceDocument.parse("""
        calculatorName:x
        executionBackend:P4_MAGIC
        formula:
        1
        ---END_OF_PART---
        """);
    assertThrows(IllegalArgumentException.class,
        () -> invalidBackend.section("x").orElseThrow().runtimeMode());
  }
}
