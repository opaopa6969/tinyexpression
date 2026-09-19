package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Verifies Java and Rust public P4 frontends against the same root-dispatch corpus. */
public class P4RustRootExpressionAcceptanceTest {
  private static final Path FIXTURE = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "root-expression.tsv");

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
    }
  }
}
