package org.unlaxer.tinyexpression.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;
import org.unlaxer.tinyexpression.loader.model.FormulaInfoList;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

/**
 * FormulaInfo -> P4 parse -> (Java emit -> javac ->) execute, under every way of selecting the P4
 * parser engine (issue #183), for every backend that parses with the P4 grammar.
 *
 * <p>Selections: none (default = ubnfc), {@code p4Engine:ubnfc}, {@code p4Engine:classic},
 * {@code -Dtinyexpression.p4.engine=classic}, {@code -D...=classic} overridden by
 * {@code p4Engine:ubnfc} (the block field is more specific than the JVM-wide property), and both
 * the block field and the property spelled with the deprecated {@code legacy} alias.
 * For each, every formula must evaluate to its expected value and the calculator must carry the
 * engine that built it. Non-BMP formulas are part of the matrix.
 */
@RunWith(Parameterized.class)
public class P4EngineModeMatrixTest {

  /** resultType, formula, expected value (compared numerically for numbers, else as String). */
  record Case(String resultType, String formula, Object expected) {}

  static final List<Case> CASES = List.of(
      new Case("float", "1+2*3", 7),
      new Case("float", "if(3 > 2){10}else{0}", 10),
      new Case("float", "match{ 1 > 2 -> 1, 2 > 1 -> 2, default -> 9 }", 2),
      new Case("float", "var $base as number set if not exists 40 description='base';\n$base + 2", 42),
      new Case("float", "/*😀*/ (1+1)*5", 10),
      new Case("float", "if('😀' == '😀'){1}else{0}", 1),
      new Case("string", "'😀' + '𝄞'", "😀𝄞"),
      new Case("string", "'こんにちは😀'", "こんにちは😀"),
      new Case("string", "if(1 > 0){'😀'}else{'x'}", "😀"),
      new Case("boolean", "'😀a' == '😀a'", true),
      new Case("float", "if(1 > 0 & 2 > 1){1}else{0}", 1));

  enum Selection {
    DEFAULT(null, null, P4ParserEngine.UBNFC),
    FIELD_UBNFC("ubnfc", null, P4ParserEngine.UBNFC),
    FIELD_CLASSIC("classic", null, P4ParserEngine.CLASSIC),
    PROPERTY_CLASSIC(null, "classic", P4ParserEngine.CLASSIC),
    FIELD_BEATS_PROPERTY("ubnfc", "classic", P4ParserEngine.UBNFC),
    /** The deprecated {@code legacy} alias must still resolve to {@link P4ParserEngine#CLASSIC}. */
    FIELD_LEGACY_ALIAS("legacy", null, P4ParserEngine.CLASSIC),
    PROPERTY_LEGACY_ALIAS(null, "legacy", P4ParserEngine.CLASSIC);

    final String field;
    final String property;
    final P4ParserEngine expected;

    Selection(String field, String property, P4ParserEngine expected) {
      this.field = field;
      this.property = property;
      this.expected = expected;
    }
  }

  static final List<ExecutionBackend> P4_BACKENDS = List.of(
      ExecutionBackend.DSL_JAVA_CODE, ExecutionBackend.P4_DSL_JAVA_CODE,
      ExecutionBackend.P4_AST_EVALUATOR, ExecutionBackend.AST_EVALUATOR);

  @Parameters(name = "{0} / {1}")
  public static List<Object[]> parameters() {
    List<Object[]> out = new ArrayList<>();
    for (ExecutionBackend backend : P4_BACKENDS) {
      for (Selection selection : Selection.values()) {
        out.add(new Object[] {backend, selection});
      }
    }
    return out;
  }

  private final ExecutionBackend backend;
  private final Selection selection;

  public P4EngineModeMatrixTest(ExecutionBackend backend, Selection selection) {
    this.backend = backend;
    this.selection = selection;
  }

  @Test
  public void everyFormulaEvaluatesUnderTheSelectedEngine() {
    List<FormulaInfo> infos = withProperty(selection.property,
        () -> parse(document(backend, selection.field)));
    assertEquals(CASES.size(), infos.size());
    for (int i = 0; i < CASES.size(); i++) {
      Case testCase = CASES.get(i);
      FormulaInfo info = infos.get(i);
      Calculator calculator = info.calculator();
      String label = backend + "/" + selection + ": " + testCase.formula();
      assertEquals(label, selection.expected.id(),
          calculator.getObject(P4ParserEngine.CALCULATOR_MARKER, String.class));
      assertEquals(label, backend.name(), calculator.getObject("_tinyExecutionBackend", String.class));
      assertValue(label, testCase.expected(), calculator.apply(context()));
    }
  }

  /** The emitted Java source must not depend on which engine parsed the formula. */
  @Test
  public void generatedJavaIsIdenticalAcrossEngines() {
    if (selection != Selection.FIELD_CLASSIC) {
      return; // one comparison per backend is enough
    }
    List<FormulaInfo> classic = withProperty(null, () -> parse(document(backend, "classic")));
    List<FormulaInfo> ubnfc = withProperty(null, () -> parse(document(backend, "ubnfc")));
    for (int i = 0; i < CASES.size(); i++) {
      assertNotNull(ubnfc.get(i).javaCodeText);
      assertEquals(backend + ": " + CASES.get(i).formula(),
          classic.get(i).javaCodeText, ubnfc.get(i).javaCodeText);
    }
  }

  @Test
  public void unknownEngineValuesFailFast() {
    if (selection != Selection.DEFAULT) {
      return;
    }
    assertThrows(RuntimeException.class,
        () -> withProperty(null, () -> parse(document(backend, "combinator"))));
    RuntimeException property = assertThrows(RuntimeException.class,
        () -> withProperty("antlr", () -> parse(document(backend, null))));
    assertTrue(String.valueOf(rootMessage(property)),
        String.valueOf(rootMessage(property)).contains("tinyexpression.p4.engine"));
  }

  private static String rootMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  private static void assertValue(String label, Object expected, Object actual) {
    assertNotNull(label, actual);
    if (expected instanceof Number number) {
      assertEquals(label, number.doubleValue(), ((Number) actual).doubleValue(), 1e-9);
    } else {
      assertEquals(label, String.valueOf(expected), String.valueOf(actual));
    }
  }

  private static CalculationContext context() {
    return CalculationContext.newContext();
  }

  static String document(ExecutionBackend backend, String engineField) {
    StringBuilder builder = new StringBuilder();
    int index = 0;
    for (Case testCase : CASES) {
      builder.append("backend:").append(backend.name()).append('\n');
      if (engineField != null) {
        builder.append(P4ParserEngine.FORMULA_INFO_KEY).append(':').append(engineField).append('\n');
      }
      builder.append("calculatorName:matrix").append(index++).append('\n')
          .append("resultType:").append(testCase.resultType()).append('\n')
          .append("formula:").append('\n')
          .append(testCase.formula()).append('\n')
          .append("---END_OF_PART---").append('\n');
    }
    return builder.toString();
  }

  static List<FormulaInfo> parse(String text) {
    FormulaInfoAdditionalFields additionalFields = new FormulaInfoAdditionalFields("siteId",
        formulaInfo -> formulaInfo.calculatorName);
    FormulaInfoList list = FormulaInfoList.parse(
        text, additionalFields, Thread.currentThread().getContextClassLoader()).get();
    return list.get();
  }

  /** Runs with the property set to {@code value}, or cleared when {@code null}; then restores it. */
  static <T> T withProperty(String value, Supplier<T> action) {
    String previous = value == null
        ? System.clearProperty(P4ParserEngine.SYSTEM_PROPERTY)
        : System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, value);
    try {
      return action.get();
    } finally {
      if (previous == null) {
        System.clearProperty(P4ParserEngine.SYSTEM_PROPERTY);
      } else {
        System.setProperty(P4ParserEngine.SYSTEM_PROPERTY, previous);
      }
    }
  }
}
