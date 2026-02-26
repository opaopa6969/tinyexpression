package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.math.BigDecimal;
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
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class ThreeExecutionBackendParityTest {

  @Test
  public void testLegacyAstAndDslJavaCodeBackendsMatchOnSupportedCorpus() {
    List<Case> cases = List.of(
        new Case("1+(8/4)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("3*4-5", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("(10-2)*(7-3)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if   (true){1}else{2}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("/*head*/if(true){1}else{2}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if/*c*/(true){1}else{2}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(true){'ok'}else{'ng'}", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("match{1==1->3,default->5}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==1->'A',default->'B'}", new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("match{1==0->false,default->true}",
            new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), null),
        new Case("'payload'", new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("$payload", new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
            context -> context.setObject("payload", "ctx-object")),
        new Case("call provide()\nobject provide(){\n'ok'\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("call identity('payload')\nobject identity($payload as object){\n$payload\n}",
            new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float), null),
        new Case("call identity(1)\nfloat identity($amount as number){\n$amount\n}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $amount as number set if not exists 100 description='amount';\n"
            + "call identity(1)\n"
            + "float identity($amount as number){\n"
            + "$amount\n"
            + "}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $name as string set if not exists 'neo' description='name';\n$name",
            new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists 3 description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists match{1==1->3,default->5} description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $base as number set if not exists 10 description='base';\n"
            + "var $delta as number set if not exists 2 description='delta';\n"
            + "$base+$delta",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null));
    runParityCases(cases, true);
  }

  @Test
  public void testLegacyAstAndDslJavaCodeBackendsMatchOnRegressionCorpus() {
    List<Case> cases = List.of(
        new Case("1+1", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("(1+1)/3+sin(30)", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(true){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(false){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(not(false)){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(10==20){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(10<=20){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(not(not(2*5>5))){6*8}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(not(not(0.7*5>5))){6*8}else{0.3*0.2}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(true|false){1}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(false|false|false|true){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(false&true){1}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(true^true){1}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(not(true|1==1)){10}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(\"opa\"==\"opa\"){1}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(\"opa\"!=\"opa\"){1}else{0}", new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if((\"opa\"+\"opa\"+\"6969\")==\"opaopa6969\"){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if('deadbeaf'[1:3]=='ea'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if('gateman'[::-1]=='nametag'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if('1a2b3'[::2]=='123'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if('1a2b3'[1::2]=='ab'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(trim(' opa 133 ')=='opa 133'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(toUpperCase('AlmondChocolate')=='ALMONDCHOCOLATE'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("if(toLowerCase('AlmondChocolate')!='almondchocolate'){1}else{0}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==1->3,default->5}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("/*head*/match/*c*/{1==1->3,default->5}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("match{1==0->false,default->true}",
            new SpecifiedExpressionTypes(ExpressionTypes._boolean, ExpressionTypes._float), null),
        new Case("match{1==1->'A',default->'B'}",
            new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists 3 description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $price as number set if not exists /*pre*/match/*m*/{1==1->3,default->5} description='price';\n$price+2",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null),
        new Case("var $name as string set if not exists 'neo' description='name';\n$name",
            new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float), null),
        new Case("call identity(1)\nfloat identity($amount as number){\n$amount\n}",
            new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float), null));
    runParityCases(cases, false);
  }

  private void runParityCases(List<Case> cases, boolean requireAstNonFallback) {
    for (int i = 0; i < cases.size(); i++) {
      Case testCase = cases.get(i);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

      Calculator legacy = createCalculatorOrThrow(ExecutionBackend.JAVA_CODE, testCase, i, classLoader);
      Calculator ast = createCalculatorOrThrow(ExecutionBackend.AST_EVALUATOR, testCase, i, classLoader);
      Calculator dslJava = createCalculatorOrThrow(ExecutionBackend.DSL_JAVA_CODE, testCase, i, classLoader);

      CalculationContext legacyContext = CalculationContext.newConcurrentContext();
      CalculationContext astContext = CalculationContext.newConcurrentContext();
      CalculationContext dslJavaContext = CalculationContext.newConcurrentContext();
      if (testCase.preparation != null) {
        testCase.preparation.accept(legacyContext);
        testCase.preparation.accept(astContext);
        testCase.preparation.accept(dslJavaContext);
      }

      Object legacyValue = legacy.apply(legacyContext);
      Object astValue = ast.apply(astContext);
      Object dslJavaValue = dslJava.apply(dslJavaContext);

      assertEquivalent(testCase.formula, legacyValue, astValue);
      assertEquivalent(testCase.formula, legacyValue, dslJavaValue);
      if (requireAstNonFallback) {
        assertNotEquals("formula=" + testCase.formula, "javacode-fallback",
            ast.getObject("_astEvaluatorRuntime", String.class));
      }
      assertEquals("dsl-javacode", dslJava.getObject("_tinyExecutionMode", String.class));
    }
  }

  private Calculator createCalculatorOrThrow(ExecutionBackend backend, Case testCase, int index, ClassLoader classLoader) {
    try {
      return CalculatorCreatorRegistry.forBackend(backend).create(
          new Source(testCase.formula), "ThreeWay_" + backend + "_" + index, testCase.types, classLoader);
    } catch (RuntimeException exception) {
      throw new AssertionError(
          "backend=" + backend + ", index=" + index + ", formula=" + testCase.formula,
          exception);
    }
  }

  private void assertEquivalent(String formula, Object expected, Object actual) {
    if (expected instanceof Number left && actual instanceof Number right) {
      BigDecimal l = new BigDecimal(String.valueOf(left));
      BigDecimal r = new BigDecimal(String.valueOf(right));
      assertEquals("formula=" + formula, 0, l.compareTo(r));
      return;
    }
    assertEquals("formula=" + formula, String.valueOf(expected), String.valueOf(actual));
  }

  private record Case(String formula, SpecifiedExpressionTypes types, Consumer<CalculationContext> preparation) {
    private Case {
      Objects.requireNonNull(formula, "formula");
      Objects.requireNonNull(types, "types");
    }
  }
}
