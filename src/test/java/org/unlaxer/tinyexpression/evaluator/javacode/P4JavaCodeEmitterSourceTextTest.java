package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BinaryExpr;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Bridges both the published String slice fields and future node-valued fields. */
public class P4JavaCodeEmitterSourceTextTest {
  private static final SpecifiedExpressionTypes TYPES =
      new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);

  @Test
  public void legacyConstructorsKeepLexicalSliceExpressions() throws Exception {
    // In particular, these null calls must remain source-compatible and unambiguous.
    Object[] emitters = {
        new P4DefaultJavaCodeEmitter(TYPES), new P4DefaultJavaCodeEmitter(TYPES, null),
        new P4TypedJavaCodeEmitter(TYPES), new P4TypedJavaCodeEmitter(TYPES, null),
        templateDelegate(new P4TemplateJavaCodeEmitter(TYPES)),
        templateDelegate(new P4TemplateJavaCodeEmitter(TYPES, null))
    };
    for (Object emitter : emitters) {
      assertNull(render(emitter, null));
      assertNull(render(emitter, " \t\n"));
      assertEquals("-2", render(emitter, " -2 "));
      assertEquals("1 + 2", render(emitter, " 1 + 2 "));
      assertEquals("customIndex()", render(emitter, "customIndex()"));
    }
  }

  @Test
  public void ownedNodeAndLexicalValueEmitIdenticalRawExpressions() throws Exception {
    BinaryExpr node = new BinaryExpr(null, List.of("1"), List.of());
    // Offsets are code points, not UTF-16 indices. Do not evaluate the node (which is "1").
    P4SourceText source = P4SourceText.fromSnapshot("😀  1 + 2  !",
        candidate -> candidate == node ? Optional.of(new int[] {1, 10}) : Optional.empty());
    Object[] emitters = {
        new P4DefaultJavaCodeEmitter(TYPES, "unrelated", source),
        new P4TypedJavaCodeEmitter(TYPES, "unrelated", source),
        templateDelegate(new P4TemplateJavaCodeEmitter(TYPES, Map.of(), source))
    };
    P4PreferredAstMapper.parseDetailed("999", ExpressionTypes._float);
    for (Object emitter : emitters) {
      assertEquals("1 + 2", render(emitter, node));
      assertEquals(render(emitter, " 1 + 2 "), render(emitter, node));
      assertEquals("-3", render(emitter, " -3 "));
      assertNull(render(emitter, null));
    }
  }

  @Test
  public void missingOwnedNodeSpanIsNotSilentlyRenderedOrEvaluated() throws Exception {
    BinaryExpr owned = new BinaryExpr(null, List.of("1"), List.of());
    BinaryExpr equalButUnowned = new BinaryExpr(null, List.of("1"), List.of());
    assertEquals(owned, equalButUnowned);
    P4SourceText source = P4SourceText.fromSnapshot("-1",
        candidate -> candidate == owned ? Optional.of(new int[] {0, 2}) : Optional.empty());
    Object[] emitters = {
        new P4DefaultJavaCodeEmitter(TYPES), new P4TypedJavaCodeEmitter(TYPES),
        new P4DefaultJavaCodeEmitter(TYPES, null, source),
        new P4TypedJavaCodeEmitter(TYPES, null, source),
        templateDelegate(new P4TemplateJavaCodeEmitter(TYPES, Map.of(), source))
    };
    for (Object emitter : emitters) {
      IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
          () -> render(emitter, equalButUnowned));
      assertTrue(error.getMessage(), error.getMessage().contains("No owned source span"));
    }
  }

  @Test
  public void emptyOwnedNodeSpanKeepsAbsentIndexBehavior() throws Exception {
    BinaryExpr node = new BinaryExpr(null, List.of(), List.of());
    P4SourceText source = P4SourceText.fromSnapshot("😀",
        candidate -> candidate == node ? Optional.of(new int[] {1, 1}) : Optional.empty());
    assertNull(render(new P4DefaultJavaCodeEmitter(TYPES, null, source), node));
    assertNull(render(new P4TypedJavaCodeEmitter(TYPES, null, source), node));
  }

  private static Object templateDelegate(P4TemplateJavaCodeEmitter emitter) throws Exception {
    var field = P4TemplateJavaCodeEmitter.class.getDeclaredField("defaultEmitter");
    field.setAccessible(true);
    return field.get(emitter);
  }

  // Published 3.0.15 still generates String-typed SliceExpr fields. Invoke the common
  // Object bridge directly so this test also exercises nodes without requiring a new mapper API.
  private static String render(Object emitter, Object value) throws Exception {
    Method method = emitter.getClass().getDeclaredMethod("renderSliceIndexExpr", Object.class);
    method.setAccessible(true);
    try {
      return (String) method.invoke(emitter, value);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof RuntimeException cause) throw cause;
      throw failure;
    }
  }
}
