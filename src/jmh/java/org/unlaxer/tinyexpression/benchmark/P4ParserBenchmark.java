package org.unlaxer.tinyexpression.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.context.Memoization;
import org.unlaxer.context.ParseOptions;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;

/** JMH coverage for the generated P4 parser and AST mapper. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(value = 2, jvmArgsAppend = {
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED"
})
// The generated mapper owns static IdentityHashMaps which it clears for each mapping.
// Keep one worker per fork and additionally serialize mapping if -t overrides this default.
@Threads(1)
public class P4ParserBenchmark {

  private static final Object MAPPER_LOCK = TinyExpressionP4Mapper.class;

  @State(Scope.Thread)
  public static class ParserState {
    @Param({"complex.tiny", "flat-arithmetic.tiny", "large-match.tiny"})
    public String fixture;

    private String source;
    private Parser rootParser;
    private Token preparsedToken;

    @Setup(Level.Trial)
    public void prepareTrial() throws IOException {
      Path fixtureDir = Path.of(System.getProperty(
          "tinyexpression.benchmark.fixtureDir", "benchmarks/fixtures"))
          .toAbsolutePath().normalize();
      Path fixturePath = fixtureDir.resolve(fixture).normalize();
      if (!fixturePath.startsWith(fixtureDir) || !Files.isRegularFile(fixturePath)) {
        throw new IllegalArgumentException("Benchmark fixture does not exist: " + fixturePath);
      }

      source = Files.readString(fixturePath, StandardCharsets.UTF_8);
      if (source.isBlank()) {
        throw new IllegalArgumentException("Benchmark fixture is empty: " + fixturePath);
      }
      rootParser = TinyExpressionP4Parsers.getRootParser();

      preparsedToken = parseFreshTokenAndValidate(false);
      TinyExpressionP4Mapper.MappedAst validationAst = mapToken(preparsedToken);
      if (validationAst.ast() == null) {
        throw new IllegalArgumentException("Fixture did not map to a generated AST: " + fixturePath);
      }
      TinyExpressionP4Mapper.MappedAst memoizedValidationAst =
          mapToken(parseFreshTokenAndValidate(true));
      if (memoizedValidationAst.ast() == null) {
        throw new IllegalArgumentException(
            "Memoized fixture did not map to a generated AST: " + fixturePath);
      }
    }

    /** Parse into the committed grammar-root token without reducing or re-rendering the tree. */
    final Token parseFreshToken(boolean memoized) {
      try (ParseContext context = newParseContext(memoized)) {
        Parsed parsed = rootParser.parse(context);
        if (!parsed.isSucceeded()) {
          throw new IllegalArgumentException("P4 parse failed for fixture " + fixture);
        }
        Token rootToken = parsed.getRootToken(false);
        for (Token committed : context.getCurrent().getTokens()) {
          if (committed.parser == rootParser) {
            return committed;
          }
        }
        return rootToken;
      }
    }

    /** Trial-only acceptance check; string materialization is deliberately outside measurement. */
    private Token parseFreshTokenAndValidate(boolean memoized) {
      try (ParseContext context = newParseContext(memoized)) {
        Parsed parsed = rootParser.parse(context);
        if (!parsed.isSucceeded()) {
          throw new IllegalArgumentException("P4 parse failed for fixture " + fixture);
        }
        String consumed = parsed.getConsumed().source.sourceAsString();
        if (!source.equals(consumed)) {
          throw new IllegalArgumentException(
              "P4 parse stopped at UTF-16 offset " + consumed.length() + " of " + source.length()
                  + " for fixture " + fixture);
        }
        Token rootToken = parsed.getRootToken(false);
        for (Token committed : context.getCurrent().getTokens()) {
          if (committed.parser == rootParser) {
            return committed;
          }
        }
        return rootToken;
      }
    }

    private ParseContext newParseContext(boolean memoized) {
      if (memoized) {
        return ParseContext.withOptions(
            StringSource.createRootSource(source),
            ParseOptions.withMemoization(Memoization.SAFE_FAILURES));
      }
      return new ParseContext(StringSource.createRootSource(source));
    }

    final TinyExpressionP4Mapper.MappedAst mapToken(Token token) {
      synchronized (MAPPER_LOCK) {
        return TinyExpressionP4Mapper.mapParsedToken(token, "FormulaExpr");
      }
    }

    final TinyExpressionP4Mapper.MappedAst mapPreparsedToken() {
      return mapToken(preparsedToken);
    }

    final TinyExpressionP4AST parseAndMap(boolean memoized) {
      return mapToken(parseFreshToken(memoized)).ast();
    }
  }

  @Benchmark
  public void parseOnlyOff(ParserState state, Blackhole blackhole) {
    blackhole.consume(state.parseFreshToken(false));
  }

  @Benchmark
  public void mapOnly(ParserState state, Blackhole blackhole) {
    blackhole.consume(state.mapPreparsedToken());
  }

  @Benchmark
  public void parseAndMapOff(ParserState state, Blackhole blackhole) {
    blackhole.consume(state.parseAndMap(false));
  }

  /** A/B candidate: safe failure memoization, kept separate from the non-memoized baseline. */
  @Benchmark
  public void parseOnlySafe(ParserState state, Blackhole blackhole) {
    blackhole.consume(state.parseFreshToken(true));
  }

  /** A/B candidate combining safe failure memoization with the generated mapper. */
  @Benchmark
  public void parseAndMapSafe(ParserState state, Blackhole blackhole) {
    blackhole.consume(state.parseAndMap(true));
  }
}
