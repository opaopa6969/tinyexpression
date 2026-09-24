package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.dsl.runtime.ScopeStore;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.p4.P4StrictMatchTypingValidator;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BinaryExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

public class P4SourceMappingTest {
  private static final Path STRICT_MATCH_ERRORS = Path.of(
      "rust", "tinyexpression-rs", "tests", "fixtures", "strict-match-errors.tsv");
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

  public static final class TreeMapper extends LegacyMapper {
    static int rootCalls;
    static int alternateCalls;
    public static Tree mapParsedTree(Token token) {
      rootCalls++;
      return new Tree(token);
    }
    public static Tree mapSubtreeTree(Token token) {
      alternateCalls++;
      return new Tree(token);
    }
  }
  public static final class Tree {
    private final NewSelection selected;
    Tree(Token token) { selected = new NewSelection(token, new Snapshot(number("12"), 1, 3)); }
    public NewSelection select(String preferred) {
      if ("fail".equals(preferred)) throw FailingTreeMapper.FAILURE;
      return selected;
    }
  }
  public static final class FailingTreeMapper extends LegacyMapper {
    static final IllegalStateException FAILURE = new IllegalStateException("tree mapping failed");
    public static Tree mapParsedTree(Token token) { throw FAILURE; }
    public static Tree mapSubtreeTree(Token token) { throw FAILURE; }
  }
  public static final class BrokenTreeMapper extends LegacyMapper {
    public static Object mapParsedTree(Token token) { return new Object(); }
  }

  @Test public void mapOnceMapsOnlyOnceAndDispatchesTheEntryPoint() {
    for (var entryPoint : P4SourceMapping.EntryPoint.values()) {
      LegacyMapper.calls = TreeMapper.rootCalls = TreeMapper.alternateCalls = 0;
      Token token = emptyToken();
      var tree = P4SourceMapping.mapOnce(TreeMapper.class, token, emptyToken(), "😀12", entryPoint);
      var first = tree.select("BinaryExpr");
      for (String candidate : Arrays.asList("BinaryExpr", "MissingExpr", "", null)) {
        var selected = tree.select(candidate);
        assertSame(token, selected.token());
        assertSame(first.ast(), selected.ast());
        assertEquals("12", selected.sourceText().text(selected.ast()));
        assertArrayEquals(new int[]{1, 3}, selected.sourceText().spanOf(selected.ast()).orElseThrow());
      }
      assertEquals(entryPoint == P4SourceMapping.EntryPoint.ROOT ? 1 : 0, TreeMapper.rootCalls);
      assertEquals(entryPoint == P4SourceMapping.EntryPoint.ALTERNATE ? 1 : 0, TreeMapper.alternateCalls);
      assertEquals(0, LegacyMapper.calls);
    }
  }

  @Test public void mapOnceFallsBackToExistingSelectorsAndLegacyMapping() {
    Token root = emptyToken(), legacy = emptyToken();
    for (Class<?> mapper : List.of(LegacyMapper.class, LegacyOwnedMapper.class,
        SnapshotMapper.class, EntryPointMapper.class)) {
      for (var entryPoint : P4SourceMapping.EntryPoint.values()) {
        LegacyMapper.calls = 0;
        var tree = P4SourceMapping.mapOnce(mapper, root, legacy, "😀12345", entryPoint);
        assertEquals("fallback must defer mapping until selection", 0, LegacyMapper.calls);
        for (String candidate : List.of("12", "345")) {
          var actual = tree.select(candidate);
          var expected = P4SourceMapping.select(mapper, root, legacy, candidate, "😀12345", entryPoint);
          assertEquivalentSelection(expected, actual);
          boolean snapshot = mapper == EntryPointMapper.class
              || mapper == SnapshotMapper.class && entryPoint == P4SourceMapping.EntryPoint.ROOT;
          assertSame(snapshot ? root : legacy, actual.token());
        }
        if (mapper == LegacyMapper.class) assertEquals(4, LegacyMapper.calls);
      }
    }
  }

  @Test public void mapOnceFailuresNeverRetryLegacyMapping() {
    LegacyMapper.calls = 0;
    for (var entryPoint : P4SourceMapping.EntryPoint.values()) {
      assertSame(FailingTreeMapper.FAILURE, assertThrows(IllegalStateException.class,
          () -> P4SourceMapping.mapOnce(FailingTreeMapper.class, null, null, "12", entryPoint)));
      var tree = P4SourceMapping.mapOnce(TreeMapper.class, null, null, "12", entryPoint);
      assertSame(FailingTreeMapper.FAILURE, assertThrows(IllegalStateException.class,
          () -> tree.select("fail")));
    }
    assertThrows(IllegalStateException.class, () -> P4SourceMapping.mapOnce(
        BrokenTreeMapper.class, null, null, "12", P4SourceMapping.EntryPoint.ROOT));
    var fallback = P4SourceMapping.mapOnce(
        FailingMapper.class, null, null, "12", P4SourceMapping.EntryPoint.ROOT);
    assertSame(FailingMapper.FAILURE, assertThrows(IllegalArgumentException.class,
        () -> fallback.select("12")));
    assertEquals(0, LegacyMapper.calls);
  }

  @Test public void realMapOnceMatchesPerCandidateRootSelections() throws Exception {
    for (String source : List.of("1 + 2 * 3", "'😀' + 'x'", "var $n as number set 3; $n + 4")) {
      assertRealSelections(TinyExpressionP4Parsers.getRootParser(), source, P4SourceMapping.EntryPoint.ROOT);
    }
  }

  @Test public void realMapOnceMatchesPerCandidateAlternateSelections() throws Exception {
    assertRealSelections(Parser.get(TinyExpressionP4Parsers.StringExpressionParser.class),
        "'😀' + 'x'", P4SourceMapping.EntryPoint.ALTERNATE);
    assertRealSelections(Parser.get(TinyExpressionP4Parsers.BooleanExpressionParser.class),
        "1 < 2 & 2 < 3", P4SourceMapping.EntryPoint.ALTERNATE);
    assertRealSelections(Parser.get(TinyExpressionP4Parsers.ObjectExpressionParser.class),
        "$o as object", P4SourceMapping.EntryPoint.ALTERNATE);
  }

  private static void assertRealSelections(Parser parser, String source,
      P4SourceMapping.EntryPoint entryPoint) throws Exception {
    boolean hasTree;
    try {
      TinyExpressionP4Mapper.class.getMethod(entryPoint == P4SourceMapping.EntryPoint.ROOT
          ? "mapParsedTree" : "mapSubtreeTree", Token.class);
      hasTree = true;
    } catch (NoSuchMethodException absent) {
      hasTree = false;
    }
    String expectedCapability = System.getProperty("tinyexpression.expected.mapper.mappedTree");
    if (expectedCapability != null) {
      assertEquals("wrong generator on test classpath", Boolean.parseBoolean(expectedCapability), hasTree);
    }
    System.out.println("P4 mapOnce " + entryPoint + ": " + (hasTree ? "mapped tree" : "fallback"));
    try (var context = new ParseContext(StringSource.createRootSource(source))) {
      ScopeStore.registerDispatcher(context);
      var parsed = parser.parse(context);
      assertTrue(source, parsed.isSucceeded());
      assertEquals(source, parsed.getConsumed().source.sourceAsString());
      Token root = context.getCurrent().getTokens().stream()
          .filter(token -> token.parser == parser).findFirst().orElse(parsed.getRootToken(false));
      Token legacy = parsed.getRootToken(true);
      var tree = P4SourceMapping.mapOnce(root, legacy, source, entryPoint);
      var candidates = new ArrayList<>(P4PreferredAstMapper.preferredAstSimpleNames(source));
      candidates.addAll(Arrays.asList("MissingExpr", "", "  ", null));
      for (String candidate : candidates) {
        var actual = tree.select(candidate);
        var expected = P4SourceMapping.select(root, legacy, candidate, source, entryPoint);
        assertEquivalentSelection(expected, actual);
        if (hasTree) {
          assertNotSame(expected.ast(), actual.ast());
          assertSame("later mappings must not invalidate the tree", actual.ast(), tree.select(candidate).ast());
        }
      }
    }
  }

  private static void assertEquivalentSelection(
      P4SourceMapping.Selection expected, P4SourceMapping.Selection actual) {
    assertSame(expected.token(), actual.token());
    assertEquals(expected.ast(), actual.ast());
    assertArrayEquals(expected.sourceText().spanOf(expected.ast()).orElse(null),
        actual.sourceText().spanOf(actual.ast()).orElse(null));
    if (expected.sourceText().spanOf(expected.ast()).isPresent()) {
      assertEquals(expected.sourceText().text(expected.ast()), actual.sourceText().text(actual.ast()));
    }
  }

  private static Token emptyToken() {
    return new Token(TokenKind.consumed, List.of(), TinyExpressionP4Parsers.getRootParser(), 0);
  }

  @Test public void mapOnceRootFailureKeepsCandidateAndDefaultExceptionBehavior() throws Exception {
    // A malformed token fails with a RuntimeException other than IllegalArgumentException.
    Token root = emptyToken();
    root.parser = null;
    RuntimeException expected = assertThrows(RuntimeException.class, () -> P4SourceMapping.select(
        root, root, "BinaryExpr", "", P4SourceMapping.EntryPoint.ROOT));
    for (boolean allowDefault : List.of(false, true)) {
      RuntimeException actual = assertThrows(RuntimeException.class,
          () -> mapCandidates(root, Arrays.asList(null, " ", "MissingExpr", "BinaryExpr"), allowDefault));
      assertEquals(expected.getMessage(), actual.getMessage());
      assertEquals(allowDefault ? expected.getClass() : IllegalArgumentException.class, actual.getClass());
      if (!allowDefault && !(expected instanceof IllegalArgumentException)) {
        assertEquals(expected.getClass(), actual.getCause().getClass());
      }
    }
    assertEquals("No whole-source generated AST mapping found: ",
        assertThrows(IllegalArgumentException.class,
            () -> mapCandidates(root, Arrays.asList(null, " "), false)).getMessage());
  }

  private static void mapCandidates(Token root, List<String> candidates, boolean allowDefault) throws Exception {
    Class<?> parsedRoot = Class.forName(LegacyP4PreferredAstMapper.class.getName() + "$ParsedRoot");
    var constructor = parsedRoot.getDeclaredConstructor(Token.class, Token.class, P4SourceMapping.EntryPoint.class);
    constructor.setAccessible(true);
    var method = LegacyP4PreferredAstMapper.class.getDeclaredMethod(
        "mapCandidates", String.class, List.class, boolean.class, parsedRoot);
    method.setAccessible(true);
    try {
      method.invoke(null, "", candidates, allowDefault,
          constructor.newInstance(root, root, P4SourceMapping.EntryPoint.ROOT));
    } catch (InvocationTargetException error) {
      if (error.getCause() instanceof RuntimeException failure) throw failure;
      throw error;
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

  @Test public void strictMatchDiagnosticsUseOwnedUnicodeSafeSpans() throws Exception {
    List<String[]> fixture = Files.readAllLines(STRICT_MATCH_ERRORS, StandardCharsets.UTF_8).stream()
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .map(line -> line.split("\\t", -1))
        .toList();
    for (String[] row : fixture) {
      assertEquals("fixture row: " + String.join(" | ", row), 3, row.length);
    }
    // The published parser runtime is not itself concurrent-parse safe.  This
    // test targets the stronger property introduced here: once produced, each
    // owned source snapshot stays valid across later mappings and concurrent
    // diagnostic reads.
    List<P4PreferredAstMapper.ParsedAst> parsed = fixture.stream()
        .map(row -> P4PreferredAstMapper.parseDetailed(row[1]))
        .toList();
    P4PreferredAstMapper.parseDetailed("match{true->1,default->0}");
    try (var executor = Executors.newFixedThreadPool(fixture.size())) {
      var violations = executor.invokeAll(java.util.stream.IntStream.range(0, fixture.size())
          .<Callable<P4StrictMatchTypingValidator.Violation>>mapToObj(i -> () ->
              P4StrictMatchTypingValidator.firstViolationDetail(
                  parsed.get(i).ast(), fixture.get(i)[1], parsed.get(i).sourceText())
                  .orElseThrow())
          .toList()).stream().map(future -> {
            try {
              return future.get(30, TimeUnit.SECONDS);
            } catch (Exception failure) {
              throw new AssertionError(failure);
            }
          }).toList();
      for (int i = 0; i < fixture.size(); i++) {
        String[] row = fixture.get(i);
        var violation = violations.get(i);
        assertEquals(row[0], row[2], violation.snippet());
        int startUtf16 = row[1].indexOf(row[2]);
        int expectedStart = row[1].codePointCount(0, startUtf16);
        assertEquals(row[0], expectedStart, violation.startOffset());
        assertEquals(row[0], row[2].codePointCount(0, row[2].length()), violation.length());
      }
    }
  }
}
