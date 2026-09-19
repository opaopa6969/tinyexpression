package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BinaryExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

public class P4SourceMappingTest {
  private static BinaryExpr number(String value) { return new BinaryExpr(null, List.of(value), List.of()); }

  public record LegacySelection(Token token, TinyExpressionP4AST ast) {}
  public record NewSelection(Token token, Snapshot sourceMap) {}
  public static final class Snapshot {
    private final TinyExpressionP4AST ast;
    private final Map<Object, int[]> spans = new IdentityHashMap<>();
    Snapshot(TinyExpressionP4AST ast, int start, int end) {
      this.ast = ast;
      spans.put(ast, new int[]{start, end});
    }
    public TinyExpressionP4AST ast() { return ast; }
    public Optional<int[]> sourceSpanOf(Object node) {
      return Optional.ofNullable(spans.get(node)).map(int[]::clone);
    }
  }
  public static class LegacyMapper {
    static int calls;
    public static LegacySelection mapParsedToken(Token token, String preferred) {
      calls++;
      return new LegacySelection(token, number(preferred));
    }
  }
  public static final class SnapshotMapper extends LegacyMapper {
    public static NewSelection selectParsedTokenWithSourceMap(Token token, String preferred) {
      return new NewSelection(token, new Snapshot(number(preferred), 1, 3));
    }
  }
  public static final class FailingMapper extends LegacyMapper {
    static final IllegalArgumentException FAILURE = new IllegalArgumentException("mapping failed");
    public static NewSelection selectParsedTokenWithSourceMap(Token token, String preferred) {
      throw FAILURE;
    }
  }
  public static final class BrokenMapper extends LegacyMapper {
    public static Object selectParsedTokenWithSourceMap(Token token, String preferred) { return new Object(); }
  }
  public static final class LegacyOwnedMapper {
    private static final Map<Object, int[]> SPANS = new IdentityHashMap<>();
    public static synchronized LegacySelection mapParsedToken(Token token, String preferred) {
      TinyExpressionP4AST ast = number(preferred);
      SPANS.clear();
      SPANS.put(ast, new int[]{0, preferred.codePointCount(0, preferred.length())});
      return new LegacySelection(token, ast);
    }
    public static synchronized Optional<int[]> sourceSpanOf(Object node) {
      return Optional.ofNullable(SPANS.get(node)).map(int[]::clone);
    }
  }
  public static final class LegacyUtf16LengthMapper {
    private static final Map<Object, int[]> SPANS = new IdentityHashMap<>();
    public static synchronized LegacySelection mapParsedToken(Token token, String preferred) {
      TinyExpressionP4AST ast = number(preferred);
      SPANS.clear();
      SPANS.put(ast, new int[]{1, 1 + preferred.length()});
      return new LegacySelection(token, ast);
    }
    public static synchronized Optional<int[]> sourceSpanOf(Object node) {
      return Optional.ofNullable(SPANS.get(node)).map(int[]::clone);
    }
  }
  public static final class EntryPointMapper {
    static int rootCalls;
    static int alternateCalls;
    public static NewSelection selectParsedTokenWithSourceMap(Token token, String preferred) {
      rootCalls++;
      return new NewSelection(token, new Snapshot(number(preferred), 0, preferred.length()));
    }
    public static NewSelection selectSubtreeTokenWithSourceMap(Token token, String preferred) {
      alternateCalls++;
      return new NewSelection(token, new Snapshot(number(preferred), 0, preferred.length()));
    }
  }

  @Test public void legacyCapabilityFallsBackOnlyWhenSelectorIsAbsent() {
    LegacyMapper.calls = 0;
    var legacy = P4SourceMapping.select(LegacyMapper.class, null, "12", "12");
    assertEquals(1, LegacyMapper.calls);
    assertEquals(number("12"), legacy.ast());
    assertEquals(" 12 ", legacy.sourceText().text(" 12 "));
    assertThrows(IllegalArgumentException.class, () -> legacy.sourceText().text(legacy.ast()));
  }

  @Test public void newCapabilityRetainsSnapshotAcrossLaterMappingAndRejectsEqualUnownedNodes() {
    LegacyMapper.calls = 0;
    var first = P4SourceMapping.select(SnapshotMapper.class, null, "12", "😀12");
    var second = P4SourceMapping.select(SnapshotMapper.class, null, "34", "😀34");
    assertEquals(0, LegacyMapper.calls);
    assertEquals("12", first.sourceText().text(first.ast()));
    assertEquals("34", second.sourceText().text(second.ast()));
    assertThrows(IllegalArgumentException.class, () -> first.sourceText().text(number("12")));
    assertThrows(IllegalArgumentException.class, () -> first.sourceText().text(second.ast()));
  }

  @Test public void newApiFailuresAreNotHiddenByLegacyFallback() {
    LegacyMapper.calls = 0;
    assertSame(FailingMapper.FAILURE, assertThrows(IllegalArgumentException.class,
        () -> P4SourceMapping.select(FailingMapper.class, null, "12", "12")));
    assertThrows(IllegalStateException.class,
        () -> P4SourceMapping.select(BrokenMapper.class, null, "12", "12"));
    assertEquals(0, LegacyMapper.calls);
  }

  @Test public void alternateEntryUsesOnlyTheExplicitSubtreeSelector() {
    EntryPointMapper.rootCalls = 0;
    EntryPointMapper.alternateCalls = 0;
    var selected = P4SourceMapping.select(EntryPointMapper.class, null, "12", "12",
        P4SourceMapping.EntryPoint.ALTERNATE);
    assertEquals(number("12"), selected.ast());
    assertEquals(0, EntryPointMapper.rootCalls);
    assertEquals(1, EntryPointMapper.alternateCalls);
  }

  @Test public void publishedCompatibilityCopiesLiveSpansBeforeLaterMappings() {
    var first = P4SourceMapping.select(LegacyOwnedMapper.class, null, "12", "12");
    var second = P4SourceMapping.select(LegacyOwnedMapper.class, null, "345", "345");
    assertEquals("12", first.sourceText().text(first.ast()));
    assertEquals("345", second.sourceText().text(second.ast()));
    assertThrows(IllegalArgumentException.class, () -> first.sourceText().text(second.ast()));
  }

  @Test public void publishedCompatibilityNormalizesMixedUnicodeOffsets() {
    var selected = P4SourceMapping.select(
        LegacyUtf16LengthMapper.class, null, "'😀'", "x'😀'y");
    assertEquals("'😀'", selected.sourceText().text(selected.ast()));
  }

  @Test public void lexicalAndOwnedTextDoNotEvaluateOrStringifyNodes() {
    var lexical = P4SourceText.lexicalOnly();
    assertNull(lexical.text(null));
    assertNull(lexical.text(Optional.empty()));
    for (String value : List.of("", " +2 ", "1+1", "(1)", "1.5", "2147483648")) {
      assertSame(value, lexical.text(value));
      assertSame(value, lexical.text(Optional.of(value)));
    }
    Object node = new Object() {
      @Override public String toString() { throw new AssertionError("must not stringify node"); }
    };
    var owned = P4SourceText.fromSnapshot("😀 ( 1+1 ) ", value -> value == node
        ? Optional.of(new int[]{2, 9}) : Optional.empty());
    assertEquals("( 1+1 )", owned.text(node));
    assertEquals("( 1+1 )", owned.text(Optional.of(node)));
    assertThrows(IllegalArgumentException.class, () -> lexical.text(node));
    assertThrows(IllegalArgumentException.class, () -> owned.text(new Object()));
    assertEquals("", P4SourceText.fromSnapshot("😀", value -> Optional.of(new int[]{1, 1})).text(node));
  }

  @Test public void malformedSpansAreExplicitErrors() {
    for (int[] span : List.of(new int[]{}, new int[]{0}, new int[]{-1, 0}, new int[]{1, 0},
        new int[]{0, 2}, new int[]{0, 1, 1})) {
      var source = P4SourceText.fromSnapshot("😀", node -> Optional.of(span));
      assertThrows(IllegalArgumentException.class, () -> source.text(new Object()));
    }
  }

  @Test public void commentStrippingPreservesUnicodeCodePointCoordinates() {
    for (String source : List.of("/*😀*/1<2/*終*/", "//😀終\n1<2", "'/*😀*/'==\"//終\"")) {
      String stripped = TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(source);
      assertEquals(source, source.codePointCount(0, source.length()),
          stripped.codePointCount(0, stripped.length()));
      assertEquals(source, source.chars().filter(value -> value == '\n').count(),
          stripped.chars().filter(value -> value == '\n').count());
    }
  }

  @Test public void realGeneratedMapperUsesItsDeclaredCapabilityAndActualParserInput() throws Exception {
    boolean hasSnapshot;
    try {
      TinyExpressionP4Mapper.class.getMethod("selectParsedTokenWithSourceMap", Token.class, String.class);
      hasSnapshot = true;
    } catch (NoSuchMethodException absent) {
      hasSnapshot = false;
    }
    String expected = System.getProperty("tinyexpression.expected.mapper.snapshot");
    if (expected != null) assertEquals("wrong generator on test classpath", Boolean.parseBoolean(expected), hasSnapshot);
    var first = P4PreferredAstMapper.parseDetailed("/*😀*/ 2");
    P4PreferredAstMapper.parseDetailed("999");
    assertEquals("2", first.sourceText().text(first.ast()).strip());
    boolean hasSubtreeSnapshot;
    try {
      TinyExpressionP4Mapper.class.getMethod(
          "selectSubtreeTokenWithSourceMap", Token.class, String.class);
      hasSubtreeSnapshot = true;
    } catch (NoSuchMethodException absent) {
      hasSubtreeSnapshot = false;
    }
    if (expected != null && hasSnapshot) {
      assertEquals("source generator must expose the paired subtree selector",
          Boolean.parseBoolean(expected), hasSubtreeSnapshot);
    }
    var comparison = P4PreferredAstMapper.parseDetailed("/*😀*/1<2/*終*/");
    P4PreferredAstMapper.parseDetailed("8+9");
    String comparisonInput = TinyExpressionParserCapabilities
        .stripJavaStyleCommentsPreservingLayout("/*😀*/1<2/*終*/");
    assertEquals(comparisonInput.strip(), comparison.sourceText().text(comparison.ast()).strip());
    for (String unicodeExpression : List.of("'😀'", "'😀'=='😀'")) {
      var unicode = P4PreferredAstMapper.parseDetailed(unicodeExpression);
      assertEquals(unicodeExpression, unicode.sourceText().text(unicode.ast()));
    }
    String objectSource = "/*😀*/$o as object/*終*/";
    var object = P4PreferredAstMapper.parseDetailed(objectSource);
    assertEquals("explicit:object", object.selectionMode());
    assertTrue(object.ast() instanceof TinyExpressionP4AST.ObjectExpr);
    TinyExpressionP4AST.ObjectExpr objectRoot = (TinyExpressionP4AST.ObjectExpr) object.ast();
    String strippedObject = TinyExpressionParserCapabilities
        .stripJavaStyleCommentsPreservingLayout(objectSource);
    assertEquals(strippedObject.strip(), object.sourceText().text(objectRoot).strip());
    assertEquals("$o as object", object.sourceText().text(objectRoot.value()).strip());
    P4PreferredAstMapper.parseDetailed("$another as object");
    assertEquals(strippedObject.strip(), object.sourceText().text(objectRoot).strip());
    var oldConstructor = new P4PreferredAstMapper.ParsedAst(first.ast(), "compat");
    assertSame(first.ast(), oldConstructor.ast());
    assertEquals("compat", oldConstructor.selectionMode());
    assertEquals("2", oldConstructor.sourceText().text("2"));
    var detailed = P4PreferredAstMapper.parseByAstSimpleNamesDetailed("2", List.of("BinaryExpr"), 0L);
    assertEquals(P4PreferredAstMapper.parseByAstSimpleNames("2", List.of("BinaryExpr"), 0L), detailed.ast());
  }

  @Test public void realSnapshotsRemainOwnedAfterConcurrentRootAndAlternateMappings() throws Exception {
    List<String> formulas = List.of(
        "1+2", "1<2", "1<2&2<3", "/*😀*/2>=1/*終*/", "/*😀*/$o Object/*終*/");
    List<Callable<P4PreferredAstMapper.ParsedAst>> jobs = formulas.stream()
        .<Callable<P4PreferredAstMapper.ParsedAst>>map(formula ->
            () -> P4PreferredAstMapper.parseDetailed(formula)).toList();
    List<P4PreferredAstMapper.ParsedAst> parsed;
    try (var executor = Executors.newFixedThreadPool(formulas.size())) {
      parsed = executor.invokeAll(jobs).stream().map(future -> {
        try {
          return future.get(30, TimeUnit.SECONDS);
        } catch (Exception failure) {
          throw new AssertionError(failure);
        }
      }).toList();
    }
    P4PreferredAstMapper.parseDetailed("999");
    for (int i = 0; i < formulas.size(); i++) {
      String expected = TinyExpressionParserCapabilities
          .stripJavaStyleCommentsPreservingLayout(formulas.get(i)).strip();
      assertEquals(formulas.get(i), expected, parsed.get(i).sourceText().text(parsed.get(i).ast()).strip());
    }
  }

  @Test public void documentFamilyReselectionKeepsOuterAndExpressionSourceSpans() {
    String source = "var $s as string;/*😀*/$s as string";
    var parsed = P4PreferredAstMapper.parseDetailed(source);
    var formula = (TinyExpressionP4AST.FormulaExpr) parsed.ast();
    assertTrue(formula.expression().value() instanceof TinyExpressionP4AST.StringConcatExpr);
    assertEquals("$s as string", parsed.sourceText().text(formula.expression()).strip());
    assertEquals("$s as string", parsed.sourceText().text(formula.expression().value()).strip());
    P4PreferredAstMapper.parseDetailed("$other as boolean");
    assertEquals(TinyExpressionParserCapabilities
            .stripJavaStyleCommentsPreservingLayout(source).strip(),
        parsed.sourceText().text(formula).strip());
  }
}
