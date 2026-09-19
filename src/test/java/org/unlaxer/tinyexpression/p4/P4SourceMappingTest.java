package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BinaryExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

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
    if (hasSnapshot) assertEquals("2", first.sourceText().text(first.ast()).strip());
    else assertThrows(IllegalArgumentException.class, () -> first.sourceText().text(first.ast()));
    var oldConstructor = new P4PreferredAstMapper.ParsedAst(first.ast(), "compat");
    assertSame(first.ast(), oldConstructor.ast());
    assertEquals("compat", oldConstructor.selectionMode());
    assertEquals("2", oldConstructor.sourceText().text("2"));
    var detailed = P4PreferredAstMapper.parseByAstSimpleNamesDetailed("2", List.of("BinaryExpr"), 0L);
    assertEquals(P4PreferredAstMapper.parseByAstSimpleNames("2", List.of("BinaryExpr"), 0L), detailed.ast());
  }
}
