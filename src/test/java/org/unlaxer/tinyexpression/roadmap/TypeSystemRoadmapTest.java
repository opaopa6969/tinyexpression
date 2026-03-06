package org.unlaxer.tinyexpression.roadmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.unlaxer.Name;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.PreConstructedCalculator;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculatorV3;
import org.unlaxer.tinyexpression.evaluator.javacode.NumberExpressionBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.evaluator.javacode.SimpleJavaCodeBuilder;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.evaluator.javacode.TinyExpressionTokens;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public class TypeSystemRoadmapTest {

  @Test
  public void testUnifiedNumericTypeRoadmapFromShortToBigDecimal() {
    List<ExpressionType> declaredNumericTypes = List.of(
        ExpressionTypes._short,
        ExpressionTypes._byte,
        ExpressionTypes._int,
        ExpressionTypes._long,
        ExpressionTypes._float,
        ExpressionTypes._double,
        ExpressionTypes.bigInteger,
        ExpressionTypes.bigDecimal);

    for (ExpressionType type : declaredNumericTypes) {
      assertTrue(type.isNumber());
    }

    // First concrete P3 flow: parser -> AST -> codegen for currently covered primitive families.
    Map<ExpressionType, String> expectedCodeByType = new LinkedHashMap<>();
    expectedCodeByType.put(ExpressionTypes._short, "(1+2)");
    expectedCodeByType.put(ExpressionTypes._byte, "(1+2)");
    expectedCodeByType.put(ExpressionTypes._int, "(1+2)");
    expectedCodeByType.put(ExpressionTypes._long, "(1L+2L)");
    expectedCodeByType.put(ExpressionTypes._float, "(1.0f+2.0f)");
    expectedCodeByType.put(ExpressionTypes._double, "(1.0d+2.0d)");
    expectedCodeByType.put(ExpressionTypes.bigInteger,
        "(new java.math.BigInteger(\"1\").add(new java.math.BigInteger(\"2\")))");
    expectedCodeByType.put(ExpressionTypes.bigDecimal,
        "(new java.math.BigDecimal(\"1\").add(new java.math.BigDecimal(\"2\")))");

    for (Map.Entry<ExpressionType, String> entry : expectedCodeByType.entrySet()) {
      String javaCode = buildNumberExpression("1+2", entry.getKey());
      assertEquals(entry.getValue(), javaCode);
    }
  }

  @Test
  public void testUnifiedNumericTypeRoadmapRejectsInvalidBigIntegerLiteral() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> buildNumberExpression("1.5+2", ExpressionTypes.bigInteger));
    assertTrue(exception.getMessage().contains("Invalid BigInteger literal"));
  }

  @Test
  public void testJavaTypeAsFourthTypeRoadmap() {
    ExpressionType objectType = ExpressionTypes.object;

    assertTrue(objectType.isObject());
    assertTrue(!objectType.isNumber());
    assertTrue(!objectType.isString());
    assertTrue(!objectType.isBoolean());

    // First concrete javaType flow: declaration in SpecifiedExpressionTypes + codegen to Object return.
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapJavaType"),
        new Source("1+2"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());

    assertTrue(calculator.resultType().isObject());
    Object result = calculator.apply(CalculationContext.newConcurrentContext());
    assertEquals(3.0f, ((Number) result).floatValue(), 0.0001f);

    PreConstructedCalculator objectVariableCalculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapJavaTypeObjectVariable"),
        new Source("var $payload description='payload';$payload"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.setObject("payload", "hello");
    Object objectVariableResult = objectVariableCalculator.apply(context);
    assertEquals("hello", objectVariableResult);

    PreConstructedCalculator objectVariableWithDefaultCalculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapJavaTypeObjectVariableDefault"),
        new Source("var $payload set if not exists 'fallback' description='payload';$payload"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    Object objectVariableDefaultResult = objectVariableWithDefaultCalculator.apply(CalculationContext.newConcurrentContext());
    assertEquals("fallback", objectVariableDefaultResult);

    CalculationContext contextWithExistingObject = CalculationContext.newConcurrentContext();
    contextWithExistingObject.setObject("payload", "kept");
    Object objectVariablePreserved = objectVariableWithDefaultCalculator.apply(contextWithExistingObject);
    assertEquals("kept", objectVariablePreserved);

    PreConstructedCalculator objectMethodCalculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapJavaTypeObjectMethod"),
        new Source("call provide()\nobject provide(){\n 'ok'\n}"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    Object objectMethodResult = objectMethodCalculator.apply(CalculationContext.newConcurrentContext());
    assertEquals("ok", objectMethodResult);

    PreConstructedCalculator objectMethodWithObjectParameterCalculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapJavaTypeObjectMethodWithObjectParameter"),
        new Source("call identity('payload')\nobject identity($payload as object){\n $payload\n}"),
        new SpecifiedExpressionTypes(ExpressionTypes.object, ExpressionTypes._float),
        Thread.currentThread().getContextClassLoader());
    Object objectMethodWithObjectParameterResult =
        objectMethodWithObjectParameterCalculator.apply(CalculationContext.newConcurrentContext());
    assertEquals("payload", objectMethodWithObjectParameterResult);
  }

  @Test
  public void testUnifiedNumericTypeRoadmapBigDecimalDivisionUsesContextRounding() {
    String javaCode = buildNumberExpression("1/3", ExpressionTypes.bigDecimal);
    assertEquals(
        "(new java.math.BigDecimal(\"1\").divide(new java.math.BigDecimal(\"3\"),calculateContext.scale(),calculateContext.roundingMode()))",
        javaCode);
  }

  @Test
  public void testUnifiedNumericTypeRoadmapBigDecimalDivisionEvaluatesWithContextScale() {
    PreConstructedCalculator calculator = new JavaCodeCalculatorV3(
        Name.of("RoadmapBigDecimalDivision"),
        new Source("1/3"),
        new SpecifiedExpressionTypes(ExpressionTypes.bigDecimal, ExpressionTypes.bigDecimal),
        Thread.currentThread().getContextClassLoader());

    Object result = calculator.apply(CalculationContext.newConcurrentContext());
    assertTrue(result instanceof BigDecimal);
    assertEquals("0.3333333333", ((BigDecimal) result).toPlainString());
  }

  private String buildNumberExpression(String expression, ExpressionType numberType) {
    TinyExpressionParser parser = new TinyExpressionParser();
    ParseContext parseContext = new ParseContext(new StringSource(expression));
    Parsed parsed = parser.parse(parseContext);
    Token rootToken = parsed.getRootToken(true);
    rootToken = OperatorOperandTreeCreator.SINGLETON.apply(rootToken);

    TinyExpressionTokens tinyExpressionTokens = new TinyExpressionTokens(
        rootToken,
        new SpecifiedExpressionTypes(numberType, numberType));

    SimpleJavaCodeBuilder builder = new SimpleJavaCodeBuilder();
    NumberExpressionBuilder.SINGLETON.build(builder, tinyExpressionTokens.getExpressionToken(), tinyExpressionTokens);
    String javaCode = builder.toString();
    int newline = javaCode.indexOf('\n');
    return newline < 0 ? javaCode : javaCode.substring(0, newline);
  }
}
