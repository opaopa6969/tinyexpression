package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
    // This fixes the Java CodePoint Slicer contract, not the separate AST evaluator's UTF-16 rules.
    String[] formulas = {
        "'gateman'[::-1]", "'1a2b3'[::2]", "'abcdef'[1:4]",
        "'😀a'[1:2]", "'😀a'[0:1]", "'😀a'[::-1]", "'abcdef'[::0]",
        "'abcdef'[-3:-1]", "'abcdef'[2:]", "'abcdef'[:3]", "'abcdef'[:]",
        "'abcdef'[3:3]", "'abcdef'[1:4:-1]", "'abcdef'[(1+1):5]",
        "'abcdef'[(1):4]", "'abcdef'[(1/*c*/):4]", "'abcdef'[1/*c*/+1:5]"
    };
    String[] expected = {
        "nametag", "123", "bcd", "a", "😀", "a😀", "",
        "de", "cdef", "abc", "abcdef", "", "dcb", "cde", "bcd", "bcd", "cde"
    };
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
  public void outOfRangeSliceBoundsRemainExplicitRuntimeFailures() {
    String[] formulas = {"'abc'[0:4]", "'abc'[-4:2]", "'abc'[2:1]", "'abc'[4:]"};
    var types = new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (int i = 0; i < formulas.length; i++) {
      String name = "OwnedSliceBoundsProbe" + i;
      assertTrue(formulas[i], DslGeneratedAstJavaEmitter.tryEmit(
          name, new Source(formulas[i]), types, loader).isPresent());
      var calculator = CalculatorCreatorRegistry.forBackend(ExecutionBackend.DSL_JAVA_CODE)
          .create(new Source(formulas[i]), name, types, loader);
      assertThrows(formulas[i], IndexOutOfBoundsException.class,
          () -> calculator.apply(CalculationContext.newConcurrentContext()));
    }
  }

  @Test
  public void malformedIndexSyntaxIsRejectedBeforeJavaEmission() {
    var types = new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    for (String formula : new String[] {"'abc'[oops:2]", "'abc'[1+:2]"}) {
      assertFalse(formula, DslGeneratedAstJavaEmitter.tryEmit(
          "OwnedSliceInvalidProbe", new Source(formula), types, loader).isPresent());
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
