package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class DslGeneratedAstJavaEmitterTest {

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
