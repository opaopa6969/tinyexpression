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
import java.util.stream.Stream;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ThreeExecutionBackendExtractedCorpusParityTest {

  private static final int MAX_CASES = 100;
  private static final int MIN_EXECUTED_CASES = 25;
  private static final int MIN_AST_NON_FALLBACK_CASES = 8;
  private static final String CURATED_CORPUS_RESOURCE = "/parity/three-backend-parity-corpus.txt";
  private static final String EXTRACTED_CORPUS_RESOURCE = "/parity/three-backend-extracted-corpus.escaped.txt";
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
    int astNonFallback = 0;
    Map<String, Integer> executedByCategory = new LinkedHashMap<>();
    Map<String, Integer> astNonFallbackByCategory = new LinkedHashMap<>();
    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      String category = categorize(testCase.formula());
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
      if (!"javacode-fallback".equals(ast.getObject("_astEvaluatorRuntime", String.class))) {
        astNonFallback++;
        astNonFallbackByCategory.merge(category, 1, Integer::sum);
      }
      executedByCategory.merge(category, 1, Integer::sum);
      executed++;
    }
    assertTrue(
        "executed parity cases should be >= " + MIN_EXECUTED_CASES + " (executed=" + executed + ", skipped=" + skipped + ")",
        executed >= MIN_EXECUTED_CASES);
    assertTrue(
        "ast non-fallback cases should be >= " + MIN_AST_NON_FALLBACK_CASES
            + " (astNonFallback=" + astNonFallback + ", executed=" + executed + ")",
        astNonFallback >= MIN_AST_NON_FALLBACK_CASES);
    assertTrue(
        "category coverage should include at least 3 categories (executedByCategory=" + executedByCategory + ")",
        executedByCategory.size() >= 3);
    assertTrue(
        "ast non-fallback should include at least 2 categories (astNonFallbackByCategory=" + astNonFallbackByCategory + ")",
        astNonFallbackByCategory.size() >= 2);
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
    Map<String, Case> uniqueByFormula = new LinkedHashMap<>();
    List<String> resourceExtracted = loadEscapedExtractedCorpus();
    if (!resourceExtracted.isEmpty()) {
      for (String escaped : resourceExtracted) {
        String formula = unescapeJava(escaped);
        if (!isEligibleFormula(formula)) {
          continue;
        }
        uniqueByFormula.putIfAbsent(formula, new Case(formula, ""));
      }
    } else {
      Path calculatorImplTestPath =
          Path.of("src/test/java/org/unlaxer/tinyexpression/CalculatorImplTest.java");
      String source = Files.readString(calculatorImplTestPath, StandardCharsets.UTF_8);
      Matcher matcher = CALC_WITH_LITERAL_FORMULA.matcher(source);
      while (matcher.find()) {
        String formula = unescapeJava(matcher.group(1));
        String expected = matcher.group(2);
        if (!isEligibleFormula(formula)) {
          continue;
        }
        uniqueByFormula.putIfAbsent(formula, new Case(formula, expected));
      }
    }
    for (String curatedFormula : loadCuratedCorpus()) {
      if (!isEligibleFormula(curatedFormula)) {
        continue;
      }
      uniqueByFormula.putIfAbsent(curatedFormula, new Case(curatedFormula, ""));
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

  private List<String> loadCuratedCorpus() throws Exception {
    try (var stream = ThreeExecutionBackendExtractedCorpusParityTest.class.getResourceAsStream(CURATED_CORPUS_RESOURCE)) {
      if (stream == null) {
        return List.of();
      }
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return Stream.of(content.split("\\R"))
          .map(String::strip)
          .filter(line -> !line.isEmpty())
          .filter(line -> !line.startsWith("#"))
          .toList();
    }
  }

  private List<String> loadEscapedExtractedCorpus() throws Exception {
    try (var stream = ThreeExecutionBackendExtractedCorpusParityTest.class.getResourceAsStream(EXTRACTED_CORPUS_RESOURCE)) {
      if (stream == null) {
        return List.of();
      }
      String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      return Stream.of(content.split("\\R"))
          .map(String::strip)
          .filter(line -> !line.isEmpty())
          .filter(line -> !line.startsWith("#"))
          .toList();
    }
  }

  private String categorize(String formula) {
    String normalized = formula == null ? "" : formula.strip();
    if (normalized.startsWith("if(") || normalized.startsWith("if ")) {
      return "if";
    }
    if (normalized.startsWith("match{") || normalized.startsWith("match ")) {
      return "match";
    }
    if (normalized.contains("[") && normalized.contains("]")) {
      return "slice";
    }
    if (normalized.contains("toUpperCase(")
        || normalized.contains("toLowerCase(")
        || normalized.contains("trim(")) {
      return "string-fn";
    }
    if (normalized.contains("sin(")
        || normalized.contains("cos(")
        || normalized.contains("tan(")) {
      return "math-fn";
    }
    if (normalized.contains("not(")
        || normalized.contains("|")
        || normalized.contains("&")
        || normalized.contains("^")) {
      return "logical";
    }
    return "arithmetic";
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
