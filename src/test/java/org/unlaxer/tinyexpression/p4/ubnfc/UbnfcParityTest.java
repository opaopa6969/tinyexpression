package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Permanent parity gate for the 2.0.0 engine switch (issue #183): for every formula the test suite
 * feeds {@link P4PreferredAstMapper} (324, collected in ubnfc's facade work) plus ubnfc's 16 p4-java
 * fixtures plus the non-BMP cases below, {@code legacy} and {@code ubnfc} must return the same
 * {@code ParsedAst} (canonical JSON incl. code-point spans, and selectionMode), or both fail with
 * the same exception type and message. Every row is written to
 * {@code target/ubnfc-parity/report.tsv}.
 *
 * <p>When tinyexpression is built against the <b>published</b> unlaxer-dsl 3.0.15 (Java CI, the
 * release), the legacy AST carries two known mapper bugs of that generator (see
 * {@link EngineComparison#PUBLISHED_MAPPER_VERDICT}); differences fully explained by them are
 * reported separately and nothing else is tolerated. Against the fixed generator there are none.
 */
public class UbnfcParityTest {

  /** Non-BMP inputs: spans are code-point half-open ranges in both engines. */
  static final List<EngineComparison.Case> NON_BMP = List.of(
      nonBmp("'こんにちは😀'", null),
      nonBmp("'😀' + 'x'", ExpressionTypes.string),
      nonBmp("'😀a'[1:2]", ExpressionTypes.string),
      nonBmp("/*😀*/ 'abcdef'[1:3]", ExpressionTypes.string),
      nonBmp("/*😀*/ 1 + 2", ExpressionTypes._float),
      nonBmp("var $s as string set '𝄞😀' description='音符'; $s + '!'", ExpressionTypes.string),
      nonBmp("if('😀' == '😀'){1}else{0}", ExpressionTypes._float),
      nonBmp("match{'😀' == '😀' -> '𝄞', default -> 'x'}", ExpressionTypes.string),
      nonBmp("'😀' @", null),
      nonBmp("ok😀", null));

  private static EngineComparison.Case nonBmp(String source,
      org.unlaxer.tinyexpression.parser.ExpressionType type) {
    return new EngineComparison.Case(source, "UbnfcParityTest non-BMP", type, null, false);
  }

  @Test
  public void legacyAndUbnfcReturnTheSameParsedAstForEveryFormula() throws Exception {
    List<EngineComparison.Case> corpus = new ArrayList<>(EngineComparison.corpus());
    assertTrue("corpus not loaded: " + corpus.size(), corpus.size() >= 340);
    corpus.addAll(NON_BMP);
    // legacy takes seconds on the deepest fixtures and would hit the 10 s default on a loaded
    // machine; a deadline miss is not an implementation difference, so compare without one.
    List<EngineComparison.Row> rows = EngineComparison.withParseTimeout(0L,
        () -> corpus.stream().map(EngineComparison::compare).toList());
    EngineComparison.writeReport(rows, Path.of("target/ubnfc-parity/report.tsv"));
    System.out.println("ubnfc parity: " + rows.size() + " cases " + EngineComparison.tally(rows)
        + (EngineComparison.PUBLISHED_3_0_15_MAPPER ? " (legacy AST from published unlaxer-dsl 3.0.15)" : ""));

    List<String> unexpected = rows.stream()
        .filter(row -> !row.agrees())
        .map(row -> row.verdict() + " | " + row.testCase().origin() + " | legacy="
            + row.legacy() + " | ubnfc=" + row.ubnfc())
        .toList();
    assertEquals("legacy and ubnfc disagree (see target/ubnfc-parity/report.tsv)",
        List.of(), unexpected);

    // The published-mapper allowance must stay narrow: only the formulas known to hit those bugs.
    assertTrue("published-mapper allowance used too widely: " + EngineComparison.tally(rows),
        rows.stream().filter(row -> row.verdict().equals(EngineComparison.PUBLISHED_MAPPER_VERDICT))
            .count() <= 10);
    long accepted = rows.stream().filter(row -> row.verdict().equals("same")).count();
    long nonBmpAccepted = rows.stream()
        .filter(row -> row.verdict().equals("same") && row.testCase().nonBmp()).count();
    assertTrue("too few accepted cases: " + accepted, accepted >= 300);
    assertTrue("too few accepted non-BMP cases: " + nonBmpAccepted, nonBmpAccepted >= 6);
  }

  @Test
  public void everySpanIsACodePointRangeForNonBmpInputInBothEngines() {
    String formula = "'こんにちは😀'";
    int[] expected = {0, formula.codePointCount(0, formula.length())}; // UTF-16 length would be 9
    for (P4ParserEngine engine : P4ParserEngine.values()) {
      P4PreferredAstMapper.ParsedAst parsed =
          P4ParserEngine.with(engine, () -> P4PreferredAstMapper.parseDetailed(formula));
      assertArrayEquals(engine.id(), expected, parsed.sourceText().spanOf(parsed.ast()).orElseThrow());
    }
  }
}
