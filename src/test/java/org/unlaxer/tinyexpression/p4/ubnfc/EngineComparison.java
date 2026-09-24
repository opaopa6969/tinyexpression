package org.unlaxer.tinyexpression.p4.ubnfc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.unlaxer.tinyexpression.p4.P4ParserEngine;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.Json;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Runs the same input through {@link P4PreferredAstMapper} under {@link P4ParserEngine#LEGACY}
 * and {@link P4ParserEngine#UBNFC} in one JVM and compares canonical JSON, selectionMode and
 * failures. Differences are recorded, never dropped.
 */
final class EngineComparison {

  static final String CORPUS = "/p4/ubnfc-parity/te-formulas.json";
  static final String FIXTURES = "/p4/ubnfc-parity/fixtures";
  static final String TIMEOUT_PROPERTY = "tinyexpression.p4.parse.timeout.millis";

  record Case(String source, String origin, ExpressionType preferredType,
      String preferredAstSimpleName, boolean negative) {

    boolean nonBmp() {
      return source.codePoints().anyMatch(c -> c > 0xFFFF);
    }
  }

  record Outcome(String selectionMode, String json, String failure) {
    static Outcome ok(P4PreferredAstMapper.ParsedAst parsed) {
      return new Outcome(parsed.selectionMode(), CanonicalAst.json(parsed), null);
    }

    static Outcome failed(Throwable failure) {
      return new Outcome(null, null, failure.getClass().getName() + ": " + failure.getMessage());
    }

    boolean accepted() {
      return failure == null;
    }

    boolean deadline() {
      return failure != null
          && failure.startsWith(P4PreferredAstMapper.ParseDeadlineExceededException.class.getName());
    }
  }

  record Row(Case testCase, Outcome legacy, Outcome ubnfc, String verdict) {
    boolean agrees() {
      return verdict.equals("same") || verdict.equals("both-reject");
    }
  }

  private EngineComparison() {}

  /** 324 formulas the test suite feeds the facade + the 16 ubnfc p4-java fixtures. */
  static List<Case> corpus() throws IOException {
    var cases = new ArrayList<Case>();
    try (InputStream in = EngineComparison.class.getResourceAsStream(CORPUS)) {
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      for (Object entry : Json.array(Json.read(text))) {
        Map<String, Object> value = Json.object(entry);
        cases.add(new Case(
            (String) value.get("source"),
            (String) value.get("origin"),
            expressionType((String) value.get("preferredType")),
            (String) value.get("preferredAstSimpleName"),
            Boolean.TRUE.equals(value.get("negative"))));
      }
    }
    for (Path file : fixtureFiles()) {
      String name = file.getFileName().toString();
      cases.add(new Case(Files.readString(file), "ubnfc p4-java fixture " + name, null, null,
          name.startsWith("invalid") || name.startsWith("complex-half")
              || name.startsWith("complex-tail")));
    }
    return cases;
  }

  static List<Path> fixtureFiles() {
    URL url = EngineComparison.class.getResource(FIXTURES);
    if (url == null) {
      throw new IllegalStateException("missing test resource " + FIXTURES);
    }
    try (var stream = Files.list(Path.of(url.toURI()))) {
      return stream.filter(path -> path.getFileName().toString().endsWith(".tiny")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ExpressionType expressionType(String name) {
    return name == null || name.isBlank() ? null : ExpressionTypes.valueOf(name);
  }

  static Row compare(Case testCase) {
    Outcome legacy = run(testCase, P4ParserEngine.LEGACY);
    Outcome ubnfc = run(testCase, P4ParserEngine.UBNFC);
    return new Row(testCase, legacy, ubnfc, verdict(legacy, ubnfc));
  }

  static String verdict(Outcome legacy, Outcome ubnfc) {
    if (!legacy.accepted() || !ubnfc.accepted()) {
      if (!legacy.accepted() && !ubnfc.accepted()) {
        return legacy.failure().equals(ubnfc.failure()) ? "both-reject" : "reject-message-differs";
      }
      return legacy.accepted() ? "ubnfc-only-reject" : "legacy-only-reject";
    }
    if (!legacy.selectionMode().equals(ubnfc.selectionMode())) {
      return "mode-differs";
    }
    return legacy.json().equals(ubnfc.json()) ? "same" : "ast-differs";
  }

  static Outcome run(Case testCase, P4ParserEngine engine) {
    return P4ParserEngine.with(engine, (Supplier<Outcome>) () -> {
      try {
        if (testCase.preferredAstSimpleName() != null) {
          return Outcome.ok(P4PreferredAstMapper.parseByAstSimpleNameDetailed(
              testCase.source(), testCase.preferredAstSimpleName(), 0L));
        }
        return Outcome.ok(P4PreferredAstMapper.parseDetailed(
            testCase.source(), testCase.preferredType()));
      } catch (RuntimeException | StackOverflowError failure) {
        return Outcome.failed(failure);
      }
    });
  }

  /** Runs {@code action} with the default parse deadline set to {@code millis} (0 = none). */
  static <T> T withParseTimeout(long millis, Supplier<T> action) {
    String previous = System.setProperty(TIMEOUT_PROPERTY, Long.toString(millis));
    try {
      return action.get();
    } finally {
      if (previous == null) {
        System.clearProperty(TIMEOUT_PROPERTY);
      } else {
        System.setProperty(TIMEOUT_PROPERTY, previous);
      }
    }
  }

  static void writeReport(List<Row> rows, Path out) {
    try {
      Files.createDirectories(out.getParent());
      var text = new StringBuilder("verdict\torigin\tlegacy\tubnfc\tsource\n");
      for (Row row : rows) {
        text.append(row.verdict()).append('\t')
            .append(row.testCase().origin()).append('\t')
            .append(escape(describe(row.legacy()))).append('\t')
            .append(escape(describe(row.ubnfc()))).append('\t')
            .append(escape(row.testCase().source())).append('\n');
      }
      Files.writeString(out, text.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String describe(Outcome outcome) {
    return outcome.accepted() ? outcome.selectionMode() : outcome.failure();
  }

  private static String escape(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
  }

  static Map<String, Integer> tally(List<Row> rows) {
    var counts = new LinkedHashMap<String, Integer>();
    for (Row row : rows) {
      counts.merge(row.verdict(), 1, Integer::sum);
    }
    return counts;
  }
}
