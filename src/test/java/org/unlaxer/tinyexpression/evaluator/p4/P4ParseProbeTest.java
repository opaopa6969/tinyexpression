package org.unlaxer.tinyexpression.evaluator.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class P4ParseProbeTest {

  @Test
  public void failedGeneratedParseIsNotPromotedByHeuristics() {
    P4ParseProbe.Result result = P4ParseProbe.probe(
        "1 +",
        new SpecifiedExpressionTypes(ExpressionTypes._float, ExpressionTypes._float));

    assertFalse(result.parserUsed);
    assertFalse(result.exactParse);
    assertEquals("failed", result.probeMode);
    assertEquals("parse-failed", result.astNodeType);
  }
}
