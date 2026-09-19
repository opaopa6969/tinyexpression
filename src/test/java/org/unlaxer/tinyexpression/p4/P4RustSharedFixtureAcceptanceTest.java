package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;

/** Keeps the Java and Rust generated frontends on one checked-in source corpus. */
public class P4RustSharedFixtureAcceptanceTest {
  private static final Path FIXTURES = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures");

  @BeforeClass
  public static void requirePinnedSourceGenerator() {
    assumeTrue("enable with -Dtinyexpression.rust.shared=true after installing the pinned unlaxer source",
        Boolean.getBoolean("tinyexpression.rust.shared"));
  }

  @Test
  public void javaGeneratedFrontendAcceptsRustValidFixtures() throws IOException {
    for (String name : List.of(
        "valid-basic.tiny", "valid-unicode.tiny", "valid-multiline.tiny")) {
      assertFullParseAndFormulaRoot(read(name), name);
    }
  }

  @Test
  public void javaGeneratedFrontendRejectsRustInvalidFixture() throws IOException {
    String source = read("invalid-syntax.tiny");
    Parser rootParser = TinyExpressionP4Parsers.getRootParser();

    try (ParseContext context = new ParseContext(StringSource.createRootSource(source))) {
      Parsed parsed = rootParser.parse(context);
      assertFalse("invalid-syntax.tiny unexpectedly parsed", parsed.isSucceeded());
    }
    assertThrows(IllegalArgumentException.class, () -> TinyExpressionP4Mapper.parse(source));
  }

  private static void assertFullParseAndFormulaRoot(String source, String name) {
    Parser rootParser = TinyExpressionP4Parsers.getRootParser();
    TinyExpressionP4Mapper.MappedAst mapped;

    try (ParseContext context = new ParseContext(StringSource.createRootSource(source))) {
      Parsed parsed = rootParser.parse(context);
      assertTrue(name + " must parse", parsed.isSucceeded());
      assertEquals(name + " must be consumed in full", source,
          parsed.getConsumed().source.sourceAsString());
      mapped = TinyExpressionP4Mapper.mapParsedToken(
          parsed.getRootToken(true), "FormulaExpr");
    }

    assertTrue(name + " must map to the typed Formula root",
        mapped.ast() instanceof TinyExpressionP4AST.FormulaExpr);
    int codePointLength = source.codePointCount(0, source.length());
    assertArrayEquals(name + " typed Formula must retain its source span",
        new int[] {0, codePointLength},
        TinyExpressionP4Mapper.sourceSpanOf(mapped.ast()).orElseThrow());
    assertEquals(name + " mapped token must retain the full source", source,
        mapped.token().source.sourceAsString());
    var range = mapped.token().source.cursorRange();
    assertEquals(name + " source span start", 0,
        range.startIndexInclusive.position().value());
    assertEquals(name + " source span end", codePointLength,
        range.endIndexExclusive.position().value());
  }

  private static String read(String name) throws IOException {
    Path fixture = FIXTURES.resolve(name);
    assertTrue("shared Rust fixture must exist: " + fixture, Files.isRegularFile(fixture));
    return Files.readString(fixture, StandardCharsets.UTF_8);
  }
}
