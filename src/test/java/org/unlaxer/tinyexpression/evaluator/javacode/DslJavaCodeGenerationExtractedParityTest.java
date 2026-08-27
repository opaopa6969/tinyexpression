package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class DslJavaCodeGenerationExtractedParityTest {

  private static final int MAX_CASES = 60;
  private static final int MIN_EXECUTED = 20;
  private static final Pattern CALC_WITH_LITERAL_FORMULA = Pattern.compile(
      "calc\\(context\\s*,\\s*\"((?:\\\\.|[^\"\\\\])*)\"\\s*,\\s*new\\s+BigDecimal\\(\"([^\"]+)\"\\)\\)");

  @Test
  public void testExtractedLegacyCorpusRuntimeParity() throws Exception {
    List<String> formulas = extractedFormulas();
    assertTrue("extracted formulas should not be empty", !formulas.isEmpty());

    int executed = 0;
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    SpecifiedExpressionTypes types = new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    for (int i = 0; i < formulas.size(); i++) {
      String formula = formulas.get(i);
      Calculator legacy;
      Calculator dslJava;
      try {
        legacy = CalculatorCreatorRegistry.forBackend(ExecutionBackend.JAVA_CODE).create(
            new Source(formula), "ExtractedJavaLegacy_" + i, types, classLoader);
        dslJava = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE).create(
            new Source(formula), "ExtractedJavaDsl_" + i, types, classLoader);
      } catch (RuntimeException ignored) {
        continue;
      }
      CalculationContext context = CalculationContext.newConcurrentContext();
      Object legacyResult = legacy.apply(context);
      Object dslResult = dslJava.apply(context);
      if (legacyResult instanceof Number legacyNumber && dslResult instanceof Number dslNumber) {
        assertEquals("formula=" + formula,
            legacyNumber.doubleValue(), dslNumber.doubleValue(), 0.000_001d);
      } else {
        assertEquals("formula=" + formula, legacyResult, dslResult);
      }
      assertEquals("formula=" + formula,
          "p4-typed-emitter", dslJava.getObject("_tinyExecutionImplementation", String.class));
      assertEquals("formula=" + formula,
          false, dslJava.getObject("_tinyExecutionBridgeImplementation", Boolean.class));
      executed++;
    }
    assertTrue("executed extracted java parity cases should be >= " + MIN_EXECUTED + " but was " + executed,
        executed >= MIN_EXECUTED);
  }

  private List<String> extractedFormulas() throws Exception {
    String source = Files.readString(
        Path.of("src/test/java/org/unlaxer/tinyexpression/CalculatorImplTest.java"), StandardCharsets.UTF_8);
    Matcher matcher = CALC_WITH_LITERAL_FORMULA.matcher(source);
    Set<String> formulas = new LinkedHashSet<>();
    while (matcher.find()) {
      String formula = unescapeJava(matcher.group(1));
      if (isEligible(formula)) {
        formulas.add(formula);
      }
      if (formulas.size() >= MAX_CASES) {
        break;
      }
    }
    return formulas.stream().toList();
  }

  private boolean isEligible(String formula) {
    String text = formula == null ? "" : formula.strip();
    if (text.isEmpty()) {
      return false;
    }
    return !text.contains("$")
        && !text.contains("?")
        && !text.contains("call ")
        && !text.contains("external ")
        && !text.contains("internal ")
        && !text.contains("var ");
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

}
