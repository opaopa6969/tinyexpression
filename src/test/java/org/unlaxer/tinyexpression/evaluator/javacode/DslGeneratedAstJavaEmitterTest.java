package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.loader.model.CalculatorCreatorRegistry;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.runtime.ExecutionBackend;

public class DslGeneratedAstJavaEmitterTest {

  @Test
  public void sliceIndicesRemainRawJavaThroughOwnedParseResult() {
    String[] formulas = {"'gateman'[::-1]", "'1a2b3'[::2]", "'abcdef'[1:4]"};
    String[] expected = {"nametag", "123", "bcd"};
    var types = new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (int i = 0; i < formulas.length; i++) {
      String name = "OwnedSliceEmitterProbe" + i;
      var emitted = DslGeneratedAstJavaEmitter.tryEmit(name, new Source(formulas[i]), types, loader);
      assertTrue(formulas[i], emitted.isPresent());
      assertTrue(emitted.get().javaCode().contains("org.unlaxer.util.Slicer"));
      if (i == 0) assertTrue(emitted.get().javaCode().contains(".step((int)-1)"));
      var calculator = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE)
          .create(new Source(formulas[i]), name, types, loader);
      assertEquals(formulas[i], expected[i], calculator.apply(CalculationContext.newConcurrentContext()));
    }
  }

  @Test
  public void testLiteralFloatCanBeEmitted() {
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper");
      Method parse = mapperClass.getMethod("parse", String.class);
      Object ast = parse.invoke(null, "1");
      assertTrue("mapper should return ast for literal", ast != null);
    } catch (Throwable throwable) {
      fail("generated mapper parse failed: " + throwable);
    }

    var emitted = DslGeneratedAstJavaEmitter.tryEmit(
        "DslLiteralEmitterProbe",
        new Source("1"),
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    assertTrue("literal float should be emitted by native DSL emitter", emitted.isPresent());
  }
}
