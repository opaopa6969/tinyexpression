package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Calculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class AstEvaluatorTokenLiteralFallbackTest {

  @Test
  public void testSimpleLiteralAndVariableUseTokenAstWhenGeneratedRuntimeIsUnavailable() {
    ClassLoader blockedGeneratedRuntime = new GeneratedRuntimeBlockingClassLoader(
        Thread.currentThread().getContextClassLoader());
    List<Case> cases = List.of(
        new Case("'hello'", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), "hello", null),
        new Case("\"hello\"", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), "hello", null),
        new Case("true", new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), true, null),
        new Case("var $payload set if not exists 'fallback' description='payload';\n$payload",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), "fallback", null),
        new Case("/*pre*/var $payload set if not exists 'fallback' description='payload';\n$payload",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), "fallback", null),
        new Case("var $payload set if not exists \"fallback\" description='payload';\n$payload",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), "fallback", null),
        new Case("$payload", new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), "ctx-object",
            context -> context.setObject("payload", "ctx-object")));

    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      Calculator calculator = CalculatorCreatorRegistry.astEvaluatorCreator().create(
          new Source(testCase.formula), "AstTokenLiteral_" + i, testCase.types, blockedGeneratedRuntime);
      CalculationContext context = CalculationContext.newConcurrentContext();
      if (testCase.preparation != null) {
        testCase.preparation.accept(context);
      }
      Object value = calculator.apply(context);
      assertEquals("formula=" + testCase.formula, testCase.expected, value);
      assertEquals("formula=" + testCase.formula,
          "token-ast", calculator.getObject("_astEvaluatorRuntime", String.class));
      assertEquals("formula=" + testCase.formula,
          false, calculator.getObject("_astEvaluatorMapperAvailable", Boolean.class));
    }
  }

  private record Case(String formula, SpecifiedExpressionTypes types, Object expected,
      Consumer<CalculationContext> preparation) {
    private Case {
      Objects.requireNonNull(formula, "formula");
      Objects.requireNonNull(types, "types");
    }
  }

  private static class GeneratedRuntimeBlockingClassLoader extends ClassLoader {
    GeneratedRuntimeBlockingClassLoader(ClassLoader parent) {
      super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name != null && name.contains(".generated.p4.")) {
        throw new ClassNotFoundException(name);
      }
      return super.loadClass(name, resolve);
    }
  }
}
