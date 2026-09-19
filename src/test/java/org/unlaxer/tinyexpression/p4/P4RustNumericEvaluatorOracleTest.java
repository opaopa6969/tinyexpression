package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Cross-language oracle for the context-free Rust f32 evaluator. */
public class P4RustNumericEvaluatorOracleTest {
  private static final Path FIXTURE = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "numeric-f32.tsv");

  @Test
  public void p4TypedFloatMatchesSharedRustFixtureWithoutFallback() throws IOException {
    for (FixtureCase fixtureCase : readFixture()) {
      Calculator calculator = CalculatorCreatorRegistry.p4AstEvaluatorCreator().create(
          new Source(fixtureCase.formula()),
          "RustNumericOracle_" + fixtureCase.id().replace('-', '_'),
          new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
          Thread.currentThread().getContextClassLoader());

      Object result = calculator.apply(CalculationContext.newConcurrentContext());

      assertTrue(fixtureCase.id() + " must produce a Number", result instanceof Number);
      int actualBits = Float.floatToRawIntBits(((Number) result).floatValue());
      assertEquals(fixtureCase.id() + ": " + fixtureCase.formula(),
          fixtureCase.expectedBits(), actualBits);
      assertEquals(fixtureCase.id() + " must use the P4 typed evaluator",
          "p4-typed", calculator.getObject("_astEvaluatorRuntime", String.class));
      assertEquals(fixtureCase.id() + " must not use the embedded bridge",
          Boolean.FALSE,
          calculator.getObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", Boolean.class));
      assertNull(fixtureCase.id() + " must not record a fallback reason",
          calculator.getObject("_p4FallbackReason", String.class));
    }
  }

  private static List<FixtureCase> readFixture() throws IOException {
    assertTrue("shared f32 fixture must exist", Files.isRegularFile(FIXTURE));
    List<FixtureCase> cases = new ArrayList<>();
    for (String line : Files.readAllLines(FIXTURE, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] fields = line.split("\\t", 3);
      assertEquals("fixture row must have id, formula, and bits: " + line, 3, fields.length);
      cases.add(new FixtureCase(fields[0], fields[1],
          (int) Long.parseUnsignedLong(fields[2], 16)));
    }
    assertTrue("shared f32 fixture must not be empty", !cases.isEmpty());
    return cases;
  }

  private record FixtureCase(String id, String formula, int expectedBits) {}
}
