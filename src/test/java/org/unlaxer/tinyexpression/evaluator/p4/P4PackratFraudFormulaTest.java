package org.unlaxer.tinyexpression.evaluator.p4;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * End-to-end check for unlaxer-parser #40 (packrat memoization) on the real fraud-detection
 * formulas from issue #19 — the deeply nested boolean/if expressions that previously hung the
 * generated P4 parser for 53+ minutes via exponential backtracking.
 *
 * <p>With {@code -Dtinyexpression.p4.memoize=true} (wired into {@link P4PreferredAstMapper}), each
 * formula must parse in well under a second. The {@link Test#timeout()} is the hard guard: if
 * memoization regressed, the exponential returns and the test times out rather than passing.
 */
public class P4PackratFraudFormulaTest {

  private static final String[] FRAUD_FORMULAS = {
    "if((isPresent($countryCode)&$countryCode!=\"JP\")&((isPresent($osGroup)&toLowerCase($osGroup).in(\"ios\"))&(isPresent($browserGroup)&toLowerCase($browserGroup).contains(\"safari\")))&(((isPresent($timezone)&$timezone=='+9')&(isPresent($priorityLanguage)&not($priorityLanguage.contains('ja'))))|((isPresent($timezone)&$timezone!='+9')&(isPresent($priorityLanguage)&$priorityLanguage.contains('ja'))))){1}else{0}",
    "if((isPresent($calculated_BlackIPAddressInOtherSites)&$calculated_BlackIPAddressInOtherSites>0.0)|(isPresent($calculated_BlackCaulisCookieInOtherSites)&$calculated_BlackCaulisCookieInOtherSites>0.0)){1}else{0}",
    "if(isPresent($calculated_TorNode)&$calculated_TorNode>0.0){1}else{0}",
    "if((isPresent($userCountGroupedByCookieOnThisSite)&$userCountGroupedByCookieOnThisSite>=2)&((isPresent($os)&(not(toLowerCase($os).contains(\"linux\"))|not(toLowerCase($os).contains(\"Fire OS\"))))|(isPresent($number_accountCreationCountByIpAddress)&isPresent($userCountGroupedByCookieOnThisSite)&not($number_accountCreationCountByIpAddress - $userCountGroupedByCookieOnThisSite>=1))|(isPresent($userCountGroupedByCookieOnAllSite)&isPresent($userCountGroupedByCookieOnThisSite)&isPresent($userCountGroupedByCookieOnThisSiteOn12H)&(not($userCountGroupedByCookieOnAllSite - $userCountGroupedByCookieOnThisSite>=1)&not($userCountGroupedByCookieOnThisSite - $userCountGroupedByCookieOnThisSiteOn12H==0))))){1}else{0}",
    "if(not(isPresent($calculated_FirstAccessUserHash))){1}else{if($ForcedRelativeSuspiciousValue1){1}else{if($ForcedRelativeSuspiciousValue5){5}else{if($default_RelativeSuspiciousValue==5){5}else{if(($POST_PROCESS_OriginalSpec_CountryIsNotJapan>0.0)|($POST_PROCESS_OriginalSpec_BlackListOnOtherSites>0.0)|($POST_PROCESS_OriginalSpec_SuspiciousProvider>0.0)|($POST_PROCESS_OriginalSpec_OneUserAccessToMultiAccount>0.0)){5}else{$default_RelativeSuspiciousValue}}}}}"
  };

  private long parseMillis(String formula) {
    long start = System.nanoTime();
    try {
      P4PreferredAstMapper.parse(formula, ExpressionTypes._float);
    } catch (RuntimeException tolerated) {
      // The point of this test is parse TIME, not the parse/typing verdict; a fast failure is fine.
    }
    return (System.nanoTime() - start) / 1_000_000;
  }

  /**
   * Formulas #1–4 are the boolean / parenthesis-ambiguity shape whose exponential backtracking
   * packrat memoization collapses — they parse in well under a second (measured ~0.02–0.5s vs the
   * 53-min hang in #19). Formula #5 is the deeply nested-{@code if} shape. Packrat (parse-phase)
   * memoization alone left it at 12–30s; #49 found the real bottleneck was the mapping phase, not
   * parsing: {@code findBestMappedToken} re-constructed the same subtrees ~10M times (each via
   * per-token reflection + an unbounded {@code IdentityHashMap} of source spans). Memoizing
   * {@code mapToken} by token identity and dropping the reflection (unlaxer-dsl MapperGenerator,
   * 3.0.11) brings #5 to ~30ms — so it is now held to the same strict sub-second bound as the rest.
   */
  @Test(timeout = 90_000)
  public void fraudFormulasParseFastWithMemoization() {
    System.setProperty("tinyexpression.p4.memoize", "true");
    try {
      for (int index = 0; index < FRAUD_FORMULAS.length; index++) {
        long elapsed = parseMillis(FRAUD_FORMULAS[index]);
        System.out.println("fraud formula #" + (index + 1) + " parsed in " + elapsed + "ms (memoize on)");
        assertTrue("formula #" + (index + 1) + " should parse in well under a second with "
            + "memoization (was " + elapsed + "ms)", elapsed < 2_000);
      }
    } finally {
      System.clearProperty("tinyexpression.p4.memoize");
    }
  }
}
