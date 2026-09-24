package org.unlaxer.tinyexpression.p4.ubnfc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Differential fuzz between the {@code classic} and {@code ubnfc} engines (issue #183).
 *
 * <p>Every accepted formula of the parity corpus is lightly mutated — one token dropped,
 * duplicated, swapped with its neighbour, or one token inserted — with a fixed seed, so the
 * run is deterministic. Both engines must agree on accept/reject; when both accept, on the
 * canonical AST (spans included) and selectionMode; when both reject, on the exception type
 * and message.
 *
 * <p>Bounded for CI: sources over {@value #MAX_SOURCE_CHARS} chars are skipped, each formula
 * yields at most {@value #MUTANTS_PER_FORMULA} mutants and the total is capped at
 * {@value #MAX_MUTANTS}. A mutant on which {@code classic} exceeds the per-parse deadline
 * (its exponential backtracking, issues #19/#20) is counted and excluded from the comparison;
 * the count is bounded. Results: {@code target/ubnfc-parity/fuzz.tsv}.
 */
public class UbnfcDifferentialFuzzTest {

  static final long SEED = 0x183_2000L;
  static final int MAX_SOURCE_CHARS = 240;
  static final int MUTANTS_PER_FORMULA = 4;
  static final int MAX_MUTANTS = 1200;
  static final long CLASSIC_DEADLINE_MILLIS = 3_000L;

  private static final Pattern TOKEN = Pattern.compile(
      "'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"|\\$?[\\p{L}_][\\p{L}\\p{N}_]*|\\d+(?:\\.\\d+)?"
          + "|==|!=|<=|>=|&&|\\|\\||->|[^\\s]");

  private static final List<String> INSERTS = List.of(
      "+", "-", "*", "(", ")", "{", "}", "[", "]", ",", ";", ":", "&", "|", "==", "!",
      "1", "0.5", "'x'", "'😀'", "$x", "true", "if", "else", "match", "default", "->", "as",
      "number", "string", ".");

  @Test
  public void bothEnginesAgreeOnLightlyMutatedCorpusFormulas() throws Exception {
    List<EngineComparison.Case> mutants = mutants();
    assertTrue("too few mutants: " + mutants.size(), mutants.size() >= 500);

    List<EngineComparison.Row> rows = EngineComparison.withParseTimeout(CLASSIC_DEADLINE_MILLIS,
        () -> mutants.stream().map(EngineComparison::compare).toList());
    EngineComparison.writeReport(rows, Path.of("target/ubnfc-parity/fuzz.tsv"));

    List<EngineComparison.Row> compared = rows.stream()
        .filter(row -> !row.classic().deadline() && !row.ubnfc().deadline())
        .toList();
    int skipped = rows.size() - compared.size();
    long accepted = compared.stream().filter(row -> row.verdict().equals("same")).count();
    long rejected = compared.stream().filter(row -> row.verdict().equals("both-reject")).count();
    System.out.println("ubnfc fuzz: " + rows.size() + " mutants, " + accepted + " both accept, "
        + rejected + " both reject, " + skipped + " skipped on classic deadline; "
        + EngineComparison.tally(rows));

    List<String> disagreements = compared.stream()
        .filter(row -> !row.agrees())
        .map(row -> row.verdict() + " | " + row.testCase().origin() + " | "
            + row.testCase().source().replace("\n", "\\n") + " | classic=" + row.classic()
            + " | ubnfc=" + row.ubnfc())
        .toList();
    assertEquals("classic and ubnfc disagree on mutants (see target/ubnfc-parity/fuzz.tsv)",
        List.of(), disagreements);
    assertTrue("classic deadline skipped too many mutants: " + skipped, skipped <= rows.size() / 20);
    // Both outcomes must actually be exercised, or the mutation is not probing anything.
    assertTrue("too few accepted mutants: " + accepted, accepted >= 50);
    assertTrue("too few rejected mutants: " + rejected, rejected >= 50);
  }

  static List<EngineComparison.Case> mutants() throws Exception {
    Random random = new Random(SEED);
    Set<String> seen = new LinkedHashSet<>();
    List<EngineComparison.Case> out = new ArrayList<>();
    for (EngineComparison.Case base : EngineComparison.corpus()) {
      if (base.negative() || base.source().length() > MAX_SOURCE_CHARS
          || base.preferredAstSimpleName() != null) {
        continue;
      }
      List<int[]> tokens = tokens(base.source());
      if (tokens.isEmpty()) {
        continue;
      }
      for (int i = 0; i < MUTANTS_PER_FORMULA && out.size() < MAX_MUTANTS; i++) {
        String mutated = mutate(base.source(), tokens, random);
        String key = mutated + "\u0000" + base.preferredType();
        if (mutated.equals(base.source()) || !seen.add(key)) {
          continue;
        }
        out.add(new EngineComparison.Case(mutated, "mutant of " + base.origin(),
            base.preferredType(), null, false));
      }
    }
    return out;
  }

  private static List<int[]> tokens(String source) {
    List<int[]> tokens = new ArrayList<>();
    Matcher matcher = TOKEN.matcher(source);
    while (matcher.find()) {
      tokens.add(new int[] {matcher.start(), matcher.end()});
    }
    return tokens;
  }

  private static String mutate(String source, List<int[]> tokens, Random random) {
    int[] token = tokens.get(random.nextInt(tokens.size()));
    String text = source.substring(token[0], token[1]);
    return switch (random.nextInt(4)) {
      case 0 -> source.substring(0, token[0]) + source.substring(token[1]); // drop
      case 1 -> source.substring(0, token[1]) + " " + text + source.substring(token[1]); // duplicate
      case 2 -> { // insert before
        String insert = INSERTS.get(random.nextInt(INSERTS.size()));
        yield source.substring(0, token[0]) + insert + " " + source.substring(token[0]);
      }
      default -> { // swap with the next token
        int index = tokens.indexOf(token);
        if (index + 1 >= tokens.size()) {
          yield source.substring(0, token[0]) + source.substring(token[1]);
        }
        int[] next = tokens.get(index + 1);
        yield source.substring(0, token[0]) + source.substring(next[0], next[1])
            + source.substring(token[1], next[0]) + text + source.substring(next[1]);
      }
    };
  }
}
