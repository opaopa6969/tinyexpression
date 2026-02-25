package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class GeneratedP4NumberAstEvaluatorVariableTest {

  @Test
  public void testResolvesVariableLeafFromCalculationContext() {
    BinaryNode variableLeaf = new BinaryNode(null, List.of("$price"), List.of());
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("price", 12.5f);

    Optional<Object> result = GeneratedP4NumberAstEvaluator.tryEvaluate(variableLeaf, types, context);

    assertTrue(result.isPresent());
    assertEquals(12.5f, ((Number) result.get()).floatValue(), 0.0001f);
  }

  @Test
  public void testResolvesVariableInsideBinaryExpression() {
    BinaryNode leftVariable = new BinaryNode(null, List.of("$count"), List.of());
    BinaryNode rightLiteral = new BinaryNode(null, List.of("2"), List.of());
    BinaryNode expr = new BinaryNode(leftVariable, List.of("+"), List.of(rightLiteral));
    SpecifiedExpressionTypes types =
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float);
    CalculationContext context = CalculationContext.newConcurrentContext();
    context.set("count", 3f);

    Optional<Object> result = GeneratedP4NumberAstEvaluator.tryEvaluate(expr, types, context);

    assertTrue(result.isPresent());
    assertEquals(5f, ((Number) result.get()).floatValue(), 0.0001f);
  }

  private record BinaryNode(Object left, List<String> op, List<Object> right) {}
}
