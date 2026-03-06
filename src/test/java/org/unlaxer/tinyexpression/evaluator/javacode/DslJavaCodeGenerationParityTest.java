package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Objects;

import org.junit.Test;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class DslJavaCodeGenerationParityTest {

  @Test
  public void testLegacyAndDslJavaCodeGenerateEquivalentJavaSource() {
    List<Case> cases = List.of(
        new Case("1+(8/4)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)),
        new Case("if(true){1}else{2}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)),
        new Case("match{1==1->3,default->5}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)),
        new Case("match{1==1->'A',default->'B'}", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float)),
        new Case("var $price as number set if not exists 3 description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)),
        new Case("call identity(1)\nfloat identity($amount as number){\n$amount\n}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float)));

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      Calculator legacy = CalculatorCreatorRegistry.forBackend(ExecutionBackend.JAVA_CODE).create(
          new Source(testCase.formula), "JavaCodeParityLegacy_" + i, testCase.types, classLoader);
      Calculator dslJava = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE).create(
          new Source(testCase.formula), "JavaCodeParityDsl_" + i, testCase.types, classLoader);

      String normalizedLegacy = normalizeJavaCode(legacy.javaCode());
      String normalizedDsl = normalizeJavaCode(dslJava.javaCode());
      assertEquals("formula=" + testCase.formula, normalizedLegacy, normalizedDsl);
    }
  }

  private String normalizeJavaCode(String javaCode) {
    String normalized = javaCode == null ? "" : javaCode.replace("\r\n", "\n").replace('\r', '\n');
    normalized = normalized.replaceAll("\\bclass\\s+[A-Za-z0-9_]+", "class __CLASS__");
    normalized = normalized.replaceAll("\\bnew\\s+[A-Za-z0-9_]+\\(", "new __CLASS__(");
    return normalized.trim();
  }

  private record Case(String formula, SpecifiedExpressionTypes types) {
    private Case {
      Objects.requireNonNull(formula, "formula");
      Objects.requireNonNull(types, "types");
    }
  }
}
