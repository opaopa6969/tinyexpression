package org.unlaxer.tinyexpression.roadmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.OperatorOperandTreeCreator;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.NotImmediatesBooleanExpressionParser;

public class ErrorDiagnosabilityRoadmapTest {

  @Test
  public void testUnsupportedNumericOperationProvidesContextMessage() {
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> ExpressionTypes.object.wrapNumber());

    assertTrue(exception.getMessage().contains("wrapNumber"));
    assertTrue(exception.getMessage().contains("object"));
  }

  @Test
  public void testNumericOperationStillWorksForSupportedType() {
    Number parsed = ExpressionTypes._long.parseNumber("42");
    assertEquals(42L, parsed.longValue());
  }

  @Test
  public void testOperatorOperandTreeCreatorFallbackProvidesOwnerAndPath() {
    Token unsupported = new Token(
        TokenKind.consumed,
        java.util.List.of(),
        Parser.get(NotImmediatesBooleanExpressionParser.class),
        0);

    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> OperatorOperandTreeCreator.SINGLETON.apply(unsupported));

    assertTrue(exception.getMessage().contains("Unsupported parser in apply"));
    assertTrue(exception.getMessage().contains(NotImmediatesBooleanExpressionParser.class.getName()));
    assertTrue(exception.getMessage().contains("tokenPath="));
  }
}
