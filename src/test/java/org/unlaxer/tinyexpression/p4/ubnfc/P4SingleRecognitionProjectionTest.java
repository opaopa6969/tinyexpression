package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/** The explicit public entry remains a test oracle, never a production retry. */
public class P4SingleRecognitionProjectionTest {
  @Test
  public void typedProjectionMatchesExplicitSubtreeStructureAndEveryOwnedSpan() throws Exception {
    Path fixture = Path.of("rust/tinyexpression-rs/tests/fixtures/root-family-projection.tsv");
    for (String line : Files.readAllLines(fixture)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] fields = line.split("\t", -1);
      assertEquals(line, 4, fields.length);
      String source = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(fields[1]);
      var result = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", fields[2], source,
          new ParseOptions(true, false, false, true, java.util.Map.of()));
      assertTrue(fields[0] + " explicit entry", result.ok());
      assertEquals(fields[0] + " full recognition", source.codePointCount(0, source.length()), result.consumedCp());
      var converter = new UbnfcAstConverter(result.nodeSpans(), source);
      var expected = converter.convert(result.ast().orElseThrow());
      var spans = converter.spans();
      for (var engine : P4ParserEngine.values()) {
        var actual = P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(fields[1]));
        assertEquals(fields[0] + "/" + engine + " root", fields[3], actual.ast().getClass().getSimpleName());
        assertEquals(fields[0] + "/" + engine + " structure", expected, actual.ast());
        compareSpans(fields[0] + "/" + engine, expected, actual.ast(), spans, actual.sourceText());
        var retained = actual.sourceText();
        P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed("'👨‍👩‍👧‍👦' + 'x'"));
        compareSpans(fields[0] + "/retained/" + engine, expected, actual.ast(), spans, retained);
      }
    }
  }

  @Test
  public void bothJavaEnginesKeepTheSharedRootAndTypedFailureContract() throws Exception {
    for (var engine : P4ParserEngine.values()) {
      P4ParserEngine.with(engine, () -> {
        var test = new org.unlaxer.tinyexpression.p4.P4RustRootExpressionAcceptanceTest();
        try {
          test.javaFrontendMatchesSharedRootExpressionFixture();
          test.javaFrontendRejectsMixedMatchHintFamiliesExplicitly();
          test.javaProductionEntrypointsApplyExplicitResultFamilySelection();
        } catch (java.io.IOException failure) {
          throw new java.io.UncheckedIOException(failure);
        }
      });
    }
  }

  @Test
  public void identifierAlphabetAndIllegalUnicodeTailAreNotBroadened() {
    for (var source : List.of("$名 as string", "$𐐀 as string", "/*😀*/1<2@", "")) {
      var raw = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", "Formula", source,
          new ParseOptions(true, false, false, true, java.util.Map.of()));
      assertFalse(source, raw.ok());
      if (source.equals("/*😀*/1<2@")) {
        assertEquals(source, 8, raw.diagnostics().farthestCp());
      }
      for (var engine : P4ParserEngine.values()) {
        assertThrows(source + "/" + engine, IllegalArgumentException.class,
            () -> P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(source)));
      }
    }
  }

  @Test
  public void completeDocumentBranchesKeepOneDeclarationSetAndOwnedExpressionPositions() throws Exception {
    var fixture = Path.of("rust/tinyexpression-rs/tests/fixtures/root-document-dispatch.tsv");
    for (String line : Files.readAllLines(fixture)) {
      if (line.isEmpty() || line.startsWith("#")) continue;
      var fields = line.split("\t", -1);
      assertEquals(line, 9, fields.length);
      var type = fields[2].equals("string") ? org.unlaxer.tinyexpression.parser.ExpressionTypes.string
          : org.unlaxer.tinyexpression.parser.ExpressionTypes._boolean;
      for (var engine : P4ParserEngine.values()) {
        var parsed = P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(fields[1], type));
        var formula = (TinyExpressionP4AST.FormulaExpr) parsed.ast();
        assertEquals(fields[0], Integer.parseInt(fields[7]), formula.declarations().size());
        assertEquals(fields[0], Integer.parseInt(fields[8]), formula.methods().size());
        assertEquals(fields[0], fields[4], formula.expression().value().getClass().getSimpleName());
        assertArrayEquals(fields[0], new int[] {Integer.parseInt(fields[5]), Integer.parseInt(fields[6])},
            parsed.sourceText().spanOf(formula.expression()).orElseThrow());
        assertArrayEquals(fields[0], new int[] {0, fields[1].codePointCount(0, fields[1].length())},
            parsed.sourceText().spanOf(formula).orElseThrow());
        var calculator = P4ParserEngine.with(engine, () ->
            org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry.p4AstEvaluatorCreator().create(
                new org.unlaxer.tinyexpression.Source(fields[1]), "RootDocument",
                new org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes(type,
                    org.unlaxer.tinyexpression.parser.ExpressionTypes._float), getClass().getClassLoader()));
        P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed("'different😀'"));
        assertEquals(fields[0], fields[3], String.valueOf(calculator.apply(
            org.unlaxer.tinyexpression.CalculationContext.newContext())));
        assertEquals(fields[0], "p4-typed", calculator.getObject("_astEvaluatorRuntime", String.class));
        assertEquals(fields[0], Boolean.FALSE, calculator.getObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", Boolean.class));
        assertNull(fields[0], calculator.getObject("_p4FallbackReason", String.class));
        assertEquals(fields[0], fields[1].substring(fields[1].offsetByCodePoints(0, Integer.parseInt(fields[5])),
            fields[1].offsetByCodePoints(0, Integer.parseInt(fields[6]))),
            parsed.sourceText().text(formula.expression()));
      }
    }
  }

  @Test
  public void losingFormulaBranchesRollBackScopesReferencesAndCommittedCst() {
    var parser = org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers.getRootParser();
    for (String source : List.of("var $n as number set 1;$n<2", "var $n as number set 1;$n<2 @",
        "var $n as number set 1;$n<2 float unused($x as number){$x} @")) {
      var raw = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", "Formula", source,
          new ParseOptions(true, false, false, true, java.util.Map.of()));
      assertEquals(source, !source.endsWith("@"), raw.ok());
      assertEquals(source, raw.ok() ? 1 : 0, raw.scope().declarations().size());
      assertEquals(source, raw.ok() ? 1 : 0, raw.scope().references().size());
      assertEquals(source, raw.ok() ? 1L : 0L,
          raw.captures().stream().filter(c -> c.name().equals("expression")).count());
      if (!raw.ok()) assertTrue(source, raw.captures().isEmpty());
      try (var context = new org.unlaxer.context.ParseContext(org.unlaxer.StringSource.createRootSource(source))) {
        org.unlaxer.dsl.runtime.ScopeStore.registerDispatcher(context);
        var parsed = parser.parse(context);
        assertEquals(source, !source.endsWith("@"), parsed.isSucceeded());
        assertEquals(source, 0, org.unlaxer.dsl.runtime.ScopeStore.currentScopeDepth(context));
        assertEquals(source, parsed.isSucceeded() ? 1 : 0,
            org.unlaxer.dsl.runtime.ScopeStore.getAllDeclarations(context).size());
        assertEquals(source, parsed.isSucceeded() ? 1 : 0,
            org.unlaxer.dsl.runtime.ScopeStore.getAllReferences(context).size());
        if (parsed.isSucceeded()) {
          var formula = (TinyExpressionP4AST.FormulaExpr)
              org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper.mapParsedToken(
                  context.getCurrent().getTokens().get(0), "FormulaExpr").ast();
          assertEquals(source, 1, formula.declarations().size());
        } else {
          assertEquals(source, 0, context.getPosition(org.unlaxer.TokenKind.consumed).value());
        }
      }
    }
  }

  private static void compareSpans(String label, Object expected, Object actual,
      java.util.Map<Object, int[]> spans, P4SourceText source) throws Exception {
    if (expected instanceof TinyExpressionP4AST) {
      assertArrayEquals(label + "/" + expected.getClass().getSimpleName(), spans.get(expected),
          source.spanOf(actual).orElse(null));
      for (RecordComponent component : expected.getClass().getRecordComponents()) {
        compareSpans(label, component.getAccessor().invoke(expected), component.getAccessor().invoke(actual), spans, source);
      }
    } else if (expected instanceof List<?> list) {
      List<?> other = (List<?>) actual;
      for (int index = 0; index < list.size(); index++) compareSpans(label, list.get(index), other.get(index), spans, source);
    } else if (expected instanceof Optional<?> optional && optional.isPresent()) {
      compareSpans(label, optional.orElseThrow(), ((Optional<?>) actual).orElseThrow(), spans, source);
    }
  }
}
