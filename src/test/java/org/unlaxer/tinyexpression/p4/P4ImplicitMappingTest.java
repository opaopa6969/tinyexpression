package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Json;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/** Compares the old bounded inventory with a single default AST on the very same committed CST. */
public class P4ImplicitMappingTest {
  record Root(Token token, Token legacy, P4SourceMapping.EntryPoint entry) {}
  record Chosen(P4SourceMapping.Selection selection, String mode) {}

  private static Root parse(String source) throws Exception {
    var method = ClassicP4PreferredAstMapper.class.getDeclaredMethod("parseRootToken", String.class, long.class);
    method.setAccessible(true);
    Object parsed;
    try { parsed = method.invoke(null, source, 0L); }
    catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException error) throw error;
      throw failure;
    }
    var type = parsed.getClass();
    var token = type.getDeclaredMethod("token"); token.setAccessible(true);
    var legacy = type.getDeclaredMethod("legacyToken"); legacy.setAccessible(true);
    var entry = type.getDeclaredMethod("entryPoint"); entry.setAccessible(true);
    return new Root((Token) token.invoke(parsed), (Token) legacy.invoke(parsed),
        (P4SourceMapping.EntryPoint) entry.invoke(parsed));
  }

  private static Chosen choose(P4SourceMapping.Mapped mapped, List<String> candidates, String source) {
    for (String candidate : candidates) {
      var selected = mapped.select(candidate);
      if (selected.ast().getClass().getSimpleName().equals(candidate)
          && selected.sourceText().text(selected.ast()).strip().equals(source.strip())) {
        return new Chosen(selected, "preferred:" + candidate);
      }
    }
    return new Chosen(mapped.select(null), "default");
  }

  @Test public void singleSnapshotMatchesInventoryAcrossExistingCorpus() throws Exception {
    var sources = new ArrayList<String>();
    String text = Files.readString(Path.of("src/test/resources/p4/ubnfc-parity/te-formulas.json"));
    for (Object value : Json.array(Json.read(text))) sources.add((String) Json.object(value).get("source"));
    try (var paths = Files.list(Path.of("src/test/resources/p4/ubnfc-parity/fixtures"))) {
      for (Path path : paths.sorted().toList()) sources.add(Files.readString(path));
    }
    Set<String> expectedParseFailures = Set.of("@", "(1 +", "'😀' @", "var $n as number set 3;\n$n +",
        "", "'abc'[oops:2]", "'abc'[1+:2]", "1 +\n",
        Files.readString(Path.of("benchmarks/fixtures/complex-half.tiny")),
        Files.readString(Path.of("benchmarks/fixtures/complex-tail.tiny")));
    int compared = 0, rejectedAtParse = 0, cappedInventory = 0;
    var cappedSources = new HashSet<String>();
    for (String original : sources) {
      String source = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(original);
      Root root;
      try { root = parse(source); }
      catch (IllegalArgumentException failure) {
        assertTrue("unexpected parse rejection: " + original, expectedParseFailures.contains(original));
        rejectedAtParse++;
        continue;
      }
      assertFalse("invalid corpus input was accepted: " + original, expectedParseFailures.contains(original));
      P4SourceMapping.Mapped inventory;
      try { inventory = P4SourceMapping.mapOnce(root.token(), root.legacy(), source, root.entry()); }
      catch (IllegalArgumentException failure) {
        if (!failure.getMessage().equals("E-MAPPING-LIMIT: preferred candidates")) throw failure;
        cappedInventory++;
        cappedSources.add(original);
        continue; // These established large positives have independent expected results below.
      }
      var implicit = P4SourceMapping.mapImplicitOnce(root.token(), root.legacy(), source, root.entry());
      List<String> candidates = ClassicP4PreferredAstMapper.preferredAstSimpleNames(source);
      for (String candidate : candidates) {
        var previous = inventory.select(candidate);
        if (previous.ast().getClass().getSimpleName().equals(candidate)
            && previous.sourceText().text(previous.ast()).strip().equals(source.strip())) {
          var replacement = implicit.select(candidate);
          assertEquals(original + " / " + candidate, previous.ast(), replacement.ast());
          assertArrayEquals(previous.sourceText().spanOf(previous.ast()).orElseThrow(),
              replacement.sourceText().spanOf(replacement.ast()).orElseThrow());
        }
      }
      var expected = choose(inventory, candidates, source);
      var actual = choose(implicit, candidates, source);
      assertEquals(original, expected.mode(), actual.mode());
      assertEquals(original, expected.selection().ast(), actual.selection().ast());
      assertArrayEquals(original, expected.selection().sourceText().spanOf(expected.selection().ast()).orElseThrow(),
          actual.selection().sourceText().spanOf(actual.selection().ast()).orElseThrow());
      assertEquals(original, expected.selection().sourceText().text(expected.selection().ast()),
          actual.selection().sourceText().text(actual.selection().ast()));
      assertSame(actual.selection().ast(), implicit.select(actual.selection().ast().getClass().getSimpleName()).ast());
      compared++;
    }
    assertEquals("all accepted inventory inputs must be compared", 324, compared);
    assertEquals("only the independently identified invalid inputs fail before mapping", 12, rejectedAtParse);
    assertEquals("large inputs occur both in the formula corpus and fixture copies", 4, cappedInventory);
    assertEquals(2, cappedSources.size());
    assertTrue(cappedSources.contains(Files.readString(Path.of("benchmarks/fixtures/complex-x64.tiny"))));
    assertTrue(cappedSources.contains(Files.readString(Path.of("benchmarks/fixtures/comparison-heavy-x64.tiny"))));
    System.out.println("Implicit mapping: compared=" + compared + ", parse-rejected=" + rejectedAtParse
        + ", bounded-inventory-rejected=" + cappedInventory);
  }

  @Test public void smallIndependentExpectedModesRemainStable() {
    for (String[] row : List.of(
        new String[]{"if(true){10}else{0}", "IfExpr", "preferred:IfExpr"},
        new String[]{"sin(1)", "SinExpr", "preferred:SinExpr"},
        new String[]{"  1+2  ", "BinaryExpr", "preferred:BinaryExpr"},
        new String[]{"'😀' + 'x'", "FormulaExpr", "preferred:FormulaExpr"})) {
      var parsed = ClassicP4PreferredAstMapper.parseDetailed(row[0]);
      assertEquals(row[0], row[1], parsed.ast().getClass().getSimpleName());
      assertEquals(row[0], row[2], parsed.selectionMode());
      assertEquals(row[0].strip(), parsed.sourceText().text(parsed.ast()).strip());
    }
  }

  @Test public void establishedLargeDefaultInputsAvoidInventoryButDoNotRaiseItsCap() throws Exception {
    for (String line : Files.readAllLines(Path.of(
        "rust/tinyexpression-rs/tests/fixtures/default-mapping-inputs.tsv"))) {
      if (line.isBlank() || line.startsWith("#")) continue;
      String[] row = line.split("\\t");
      assertEquals(8, row.length);
      String source = Files.readString(Path.of(row[1]));
      assertEquals(row[2], HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      int cp = Integer.parseInt(row[3]);
      assertEquals(cp, source.codePointCount(0, source.length()));
      Root root = parse(source);
      var implicit = P4SourceMapping.mapImplicitOnce(root.token(), root.legacy(), source, root.entry());
      var selected = choose(implicit, ClassicP4PreferredAstMapper.preferredAstSimpleNames(source), source);
      assertEquals("FormulaExpr", selected.selection().ast().getClass().getSimpleName());
      assertArrayEquals(new int[]{0, cp}, selected.selection().sourceText().spanOf(selected.selection().ast()).orElseThrow());
      assertEquals(source, selected.selection().sourceText().text(selected.selection().ast()));
      var facade = ClassicP4PreferredAstMapper.parseDetailed(source);
      assertEquals(row[4], facade.ast().getClass().getSimpleName());
      assertEquals(row[5], facade.selectionMode());
      assertArrayEquals(new int[]{0, cp}, facade.sourceText().spanOf(facade.ast()).orElseThrow());
      assertEquals("E-MAPPING-LIMIT: preferred candidates", assertThrows(IllegalArgumentException.class,
          () -> P4SourceMapping.mapOnce(root.token(), root.legacy(), source, root.entry())).getMessage());
      var retained = selected.selection();
      assertThrows(IllegalArgumentException.class, () -> ClassicP4PreferredAstMapper.parseDetailed("1+2@"));
      ClassicP4PreferredAstMapper.parseDetailed("1+2");
      assertSame(retained.ast(), implicit.select(retained.ast().getClass().getSimpleName()).ast());
      assertEquals(source, retained.sourceText().text(retained.ast()));
    }
  }
}
