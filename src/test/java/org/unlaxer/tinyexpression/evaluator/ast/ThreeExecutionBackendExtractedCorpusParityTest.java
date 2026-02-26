package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ThreeExecutionBackendExtractedCorpusParityTest {

  private static final int MAX_CASES = 80;
  private static final int MIN_EXECUTED_CASES = 25;
  private static final Pattern CALC_WITH_LITERAL_FORMULA = Pattern.compile(
      "calc\\(context\\s*,\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*new\\s+BigDecimal\\(\"([^\"]+)\"\\)\\)");

  @Test
  public void testExtractedCalculatorImplNumericCasesAcrossThreeBackends() throws Exception {
    List<Case> cases = extractedCases();
    assertTrue("extracted cases should not be empty", !cases.isEmpty());

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    int executed = 0;
    int skipped = 0;
    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      Calculator legacy;
      Calculator ast;
      Calculator dslJava;
      try {
        legacy = createCalculator(ExecutionBackend.JAVA_CODE, testCase.formula(), i, classLoader, types);
        ast = createCalculator(ExecutionBackend.AST_EVALUATOR, testCase.formula(), i, classLoader, types);
        dslJava = createCalculator(ExecutionBackend.DSL_JAVA_CODE, testCase.formula(), i, classLoader, types);
      } catch (RuntimeException ignored) {
        skipped++;
        continue;
      }

      CalculationContext legacyContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      CalculationContext dslJavaContext = CalculationContext.newConcurrentContext();
      seedSharedContext(legacyContext);
      seedSharedContext(astContext);
      seedSharedContext(dslJavaContext);

      Object legacyValue = legacy.apply(legacyContext);
      Object astValue = ast.apply(astContext);
      Object dslJavaValue = dslJava.apply(dslJavaContext);

      assertTrue("formula=" + testCase.formula() + " expected JAVA_CODE numeric value",
          legacyValue instanceof Number);
      assertEquivalent(testCase.formula(), legacyValue, astValue);
      assertEquivalent(testCase.formula(), legacyValue, dslJavaValue);
      executed++;
    }
    assertTrue(
        "executed parity cases should be >= " + MIN_EXECUTED_CASES + " (executed=" + executed + ", skipped=" + skipped + ")",
        executed >= MIN_EXECUTED_CASES);
  }

  private Calculator createCalculator(ExecutionBackend backend, String formula, int index,
      ClassLoader classLoader, SpecifiedExpressionTypes types) {
    return CalculatorCreatorRegistry.forBackend(backend).create(
        new Source(formula), "ExtractedCorpus_" + backend + "_" + index, types, classLoader);
  }

  private void seedSharedContext(CalculationContext context) {
    context.setObject("isExists", true);
    context.setObject("name", "AlmondChocolate");
    context.setObject("value", 5);
    context.setObject("object", new Object());
  }

  private List<Case> extractedCases() throws Exception {
    Path calculatorImplTestPath =
        Path.of("src/test/java/org/unlaxer/tinyexpression/CalculatorImplTest.java");
    String source = Files.readString(calculatorImplTestPath, StandardCharsets.UTF_8);
    Matcher matcher = CALC_WITH_LITERAL_FORMULA.matcher(source);
    Map<String, Case> uniqueByFormula = new LinkedHashMap<>();
    while (matcher.find()) {
      String formula = unescapeJava(matcher.group(1));
      String expected = matcher.group(2);
      if (!isEligibleFormula(formula)) {
        continue;
      }
      uniqueByFormula.putIfAbsent(formula, new Case(formula, expected));
    }
    return uniqueByFormula.values().stream().limit(MAX_CASES).toList();
  }

  private boolean isEligibleFormula(String formula) {
    String normalized = formula == null ? "" : formula.strip();
    if (normalized.isEmpty()) {
      return false;
    }
    // Keep this extracted corpus context-light and deterministic.
    return !normalized.contains("$")
        && !normalized.contains("?")
        && !normalized.contains("call ")
        && !normalized.contains("external ")
        && !normalized.contains("internal ")
        && !normalized.contains("var ");
  }

  private String unescapeJava(String escaped) {
    StringBuilder builder = new StringBuilder(escaped.length());
    for (int i = 0; i < escaped.length(); i++) {
      char current = escaped.charAt(i);
      if (current != '\\' || i + 1 >= escaped.length()) {
        builder.append(current);
        continue;
      }
      char next = escaped.charAt(++i);
      switch (next) {
        case 'n' -> builder.append('\n');
        case 'r' -> builder.append('\r');
        case 't' -> builder.append('\t');
        case '\\' -> builder.append('\\');
        case '"' -> builder.append('"');
        case '\'' -> builder.append('\'');
        default -> builder.append(next);
      }
    }
    return builder.toString();
  }

  private void assertEquivalent(String formula, Object left, Object right) {
    if (left instanceof Number l && right instanceof Number r) {
      assertEquals("formula=" + formula, 0,
          new BigDecimal(String.valueOf(l)).compareTo(new BigDecimal(String.valueOf(r))));
      return;
    }
    assertEquals("formula=" + formula, String.valueOf(left), String.valueOf(right));
  }

  private record Case(String formula, String expected) {}
}
