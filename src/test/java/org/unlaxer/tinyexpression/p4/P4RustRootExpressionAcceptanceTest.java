package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/** Verifies Java and Rust public P4 frontends against the same root-dispatch corpus. */
public class P4RustRootExpressionAcceptanceTest {
  private static final Path FIXTURE = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "root-expression.tsv");
  private static final Path TYPE_ERROR_FIXTURE = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "typed-hint-errors.tsv");

  @Test
  public void javaFrontendMatchesSharedRootExpressionFixture() throws IOException {
    assertTrue("shared root-expression fixture must exist", Files.isRegularFile(FIXTURE));
    for (String line : Files.readAllLines(FIXTURE, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] fields = line.split("\\t", -1);
      assertEquals("fixture row must have id, formula, roots, and expected node: " + line,
          5, fields.length);
      String id = fields[0];
      String formula = fields[1];
      String expectedRoot = fields[2];
      String expectedNode = fields[4];

      P4PreferredAstMapper.ParsedAst parsed = P4PreferredAstMapper.parseDetailed(formula);
      assertEquals(id + " semantic root", expectedRoot,
          parsed.ast().getClass().getSimpleName());
      assertTrue(id + " must contain " + expectedNode + ": " + parsed.ast(),
          parsed.ast().toString().contains(expectedNode));
      assertEquals(id + " owned source",
          TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(formula).strip(),
          parsed.sourceText().text(parsed.ast()).strip());
    }
  }

  @Test
  public void javaFrontendRejectsMixedMatchHintFamiliesExplicitly() throws IOException {
    assertTrue("shared type-error fixture must exist", Files.isRegularFile(TYPE_ERROR_FIXTURE));
    for (String line : Files.readAllLines(TYPE_ERROR_FIXTURE, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] fields = line.split("\t", -1);
      assertEquals("fixture row must have id and formula: " + line, 2, fields.length);
      IllegalArgumentException error = assertThrows(fields[0], IllegalArgumentException.class,
          () -> P4PreferredAstMapper.parseDetailed(fields[1]));
      assertTrue(fields[0] + ": " + error.getMessage(),
          error.getMessage().contains("match type mismatch"));
    }
  }

  @Test
  public void javaProductionEntrypointsApplyExplicitResultFamilySelection() {
    record Case(String formula, ExpressionTypes expectedType, String expectedRoot) {}
    for (Case entry : List.of(
        new Case("$s as string", ExpressionTypes.string, "StringConcatExpr"),
        new Case("$b as boolean", ExpressionTypes._boolean, "BooleanOrExpr"),
        new Case("$o as object", ExpressionTypes.object, "ObjectExpr"),
        new Case("match{true->$s as string,default->$t String}",
            ExpressionTypes.string, "StringMatchExpr"),
        new Case("match{true->$b as boolean,default->$c Boolean}",
            ExpressionTypes._boolean, "BooleanMatchExpr"))) {
      P4PreferredAstMapper.ParsedAst typed = P4PreferredAstMapper.parseDetailed(
          entry.formula(), entry.expectedType());
      assertEquals(entry.formula(), entry.expectedRoot(), typed.ast().getClass().getSimpleName());

      List<String> candidates = P4PreferredAstMapper.generatedValueCandidateAstSimpleNames(
          entry.formula(), entry.expectedType());
      P4PreferredAstMapper.ParsedAst production =
          P4PreferredAstMapper.parseByAstSimpleNamesDetailed(entry.formula(), candidates, 0L);
      assertEquals(entry.formula(), entry.expectedRoot(),
          production.ast().getClass().getSimpleName());
    }
    assertEquals("StringConcatExpr", P4PreferredAstMapper.parseByAstSimpleNamesDetailed(
        "$s as string", List.of("StringConcatExpr", "VariableRefExpr"), 0L)
        .ast().getClass().getSimpleName());
    assertEquals("FormulaExpr", P4PreferredAstMapper.parseByAstSimpleNamesDetailed(
        "$s as string", List.of("FormulaExpr"), 0L).ast().getClass().getSimpleName());
    assertEquals("VariableRefExpr",
        P4PreferredAstMapper.parseByAstSimpleName("$s as string", "VariableRefExpr", 0L)
            .getClass().getSimpleName());
    assertEquals("FormulaExpr",
        P4PreferredAstMapper.parseByAstSimpleName("$s as string", null)
            .getClass().getSimpleName());

    String document = "var $s as string;$s as string";
    var documentParsed = P4PreferredAstMapper.parseByAstSimpleNamesDetailed(
        document,
        P4PreferredAstMapper.generatedValueCandidateAstSimpleNames(
            document, ExpressionTypes.string),
        0L);
    var formula = (org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.FormulaExpr)
        documentParsed.ast();
    assertTrue(formula.expression().value()
        instanceof org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.StringConcatExpr);
  }
}
