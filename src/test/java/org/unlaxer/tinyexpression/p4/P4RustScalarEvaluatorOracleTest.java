package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Cross-language oracle for context-free scalar and control-flow evaluation. */
public class P4RustScalarEvaluatorOracleTest {
  private static final Path FIXTURE = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "scalar-control.tsv");

  @Test
  public void p4TypedEvaluatorMatchesSharedRustScalarFixtureWithoutFallback() throws IOException {
    for (FixtureCase fixtureCase : readFixture()) {
      Calculator calculator = createCalculator(fixtureCase);

      if (fixtureCase.outcome() == Outcome.ERROR) {
        assertThrows(fixtureCase.id() + ": " + fixtureCase.formula(), RuntimeException.class,
            () -> calculator.apply(CalculationContext.newConcurrentContext()));
        // The explicit p4AstEvaluatorCreator path cannot fall back, but an evaluation exception
        // occurs before AstEvaluatorCalculator publishes its runtime marker.
        continue;
      }

      Object result = calculator.apply(CalculationContext.newConcurrentContext());
      assertExpectedValue(fixtureCase, result);
      assertEquals(fixtureCase.id() + " must use the P4 typed evaluator",
          "p4-typed", calculator.getObject("_astEvaluatorRuntime", String.class));
      assertEquals(fixtureCase.id() + " must not use the embedded bridge",
          Boolean.FALSE,
          calculator.getObject("_astEvaluatorGeneratedEmbeddedBridgeUsed", Boolean.class));
      assertNull(fixtureCase.id() + " must not record a fallback reason",
          calculator.getObject("_p4FallbackReason", String.class));
    }
  }

  private static Calculator createCalculator(FixtureCase fixtureCase) {
    return CalculatorCreatorRegistry.p4AstEvaluatorCreator().create(
        new Source(fixtureCase.formula()),
        "RustScalarOracle_" + fixtureCase.id().replace('-', '_'),
        new SpecifiedExpressionTypes(fixtureCase.resultType().expressionType, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
  }

  private static void assertExpectedValue(FixtureCase fixtureCase, Object result) {
    String message = fixtureCase.id() + ": " + fixtureCase.formula();
    switch (fixtureCase.resultType()) {
      case FLOAT -> {
        assertTrue(message + " must produce a Number", result instanceof Number);
        int actualBits = Float.floatToRawIntBits(((Number) result).floatValue());
        assertEquals(message, (int) Long.parseUnsignedLong(fixtureCase.expected(), 16), actualBits);
      }
      case BOOLEAN -> assertEquals(message, Boolean.parseBoolean(fixtureCase.expected()), result);
      case STRING -> assertEquals(message, fixtureCase.expected(), result);
    }
  }

  private static List<FixtureCase> readFixture() throws IOException {
    assertTrue("shared scalar fixture must exist", Files.isRegularFile(FIXTURE));
    List<FixtureCase> cases = new ArrayList<>();
    for (String line : Files.readAllLines(FIXTURE, StandardCharsets.UTF_8)) {
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] fields = line.split("\\t", -1);
      assertEquals("fixture row must have id, type, formula, outcome, and expected: " + line,
          5, fields.length);
      ResultType resultType = ResultType.parse(fields[1]);
      Outcome outcome = Outcome.parse(fields[3]);
      assertFalse("value fixture must have an expected value: " + line,
          outcome == Outcome.VALUE && fields[4].isEmpty());
      cases.add(new FixtureCase(fields[0], resultType, fields[2], outcome, fields[4]));
    }
    assertFalse("shared scalar fixture must not be empty", cases.isEmpty());
    return cases;
  }

  private enum ResultType {
    FLOAT(ExpressionTypes._float),
    BOOLEAN(ExpressionTypes._boolean),
    STRING(ExpressionTypes.string);

    private final ExpressionType expressionType;

    ResultType(ExpressionType expressionType) {
      this.expressionType = expressionType;
    }

    private static ResultType parse(String value) {
      return switch (value) {
        case "float" -> FLOAT;
        case "boolean" -> BOOLEAN;
        case "string" -> STRING;
        default -> throw new IllegalArgumentException("unknown fixture result type: " + value);
      };
    }
  }

  private enum Outcome {
    VALUE,
    ERROR;

    private static Outcome parse(String value) {
      return switch (value) {
        case "value" -> VALUE;
        case "error" -> ERROR;
        default -> throw new IllegalArgumentException("unknown fixture outcome: " + value);
      };
    }
  }

  private record FixtureCase(
      String id, ResultType resultType, String formula, Outcome outcome, String expected) {}
}
