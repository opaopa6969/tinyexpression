package org.unlaxer.tinyexpression.roadmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

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
}
