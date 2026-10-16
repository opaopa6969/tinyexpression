package org.unlaxer.tinyexpression.evaluator.p4;

import org.junit.Test;
import org.unlaxer.tinyexpression.p4.ubnfc.FraudAcceptance;

/**
 * Current production ubnfc facade: the original five fraud formulas must actually parse and map
 * to complete conditional ASTs in strictly less than one second per call. Exceptions fail.
 * Cold (first call for this input), warm-up and warm samples are reported separately.
 * This is a loaded-host regression guard, not a dedicated-host Classic SAFE performance attestation.
 */
public class P4PackratFraudFormulaTest {
  @Test(timeout = 90_000)
  public void fraudFormulasParseFastWithMemoization() throws Exception {
    FraudAcceptance.run();
  }
}
