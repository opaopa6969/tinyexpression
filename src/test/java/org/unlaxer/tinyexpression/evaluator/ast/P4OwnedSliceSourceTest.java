package org.unlaxer.tinyexpression.evaluator.ast;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.unlaxer.Token;
import org.unlaxer.tinyexpression.CalculationContext;
import org.unlaxer.tinyexpression.Source;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.SliceExpr;
import org.unlaxer.tinyexpression.p4.P4PreferredAstMapper;
import org.unlaxer.tinyexpression.p4.P4SourceText;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/** Consumer tests deliberately compile with both published and development generators. */
public class P4OwnedSliceSourceTest {
  private static final SpecifiedExpressionTypes STRING_TYPES =
      new SpecifiedExpressionTypes(ExpressionTypes.string, ExpressionTypes._float);

  private P4TypedAstEvaluator evaluator(P4SourceText sourceText) {
    return new P4TypedAstEvaluator(STRING_TYPES, CalculationContext.newConcurrentContext(), sourceText);
  }

  private Object index(P4TypedAstEvaluator evaluator, Object value) throws Exception {
    var method = P4TypedAstEvaluator.class.getDeclaredMethod("parseSliceIndex", Object.class);
    method.setAccessible(true);
    try {
      return method.invoke(evaluator, value);
    } catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Exception cause) throw cause;
      throw failure;
    }
  }

  private Object captureValue(Object value) {
    return value instanceof Optional<?> optional ? optional.orElse(null) : value;
  }

  private boolean hasOwnedSnapshotApi() {
    try {
      TinyExpressionP4Mapper.class.getMethod("selectParsedTokenWithSourceMap", Token.class, String.class);
      return true;
    } catch (NoSuchMethodException absentInPublishedGenerator) {
      return false;
    }
  }

  @Test public void stringIndicesKeepIntegerLiteralContractAndFailureClasses() throws Exception {
    var evaluator = evaluator(P4SourceText.lexicalOnly());
    assertNull(index(evaluator, null));
    assertNull(index(evaluator, " \t "));
    assertEquals(2, index(evaluator, " +2 "));
    assertEquals(-2, index(evaluator, " -2 "));
    for (String invalid : List.of("1.5", "1+1", "(1)", "$index", "2147483648")) {
      assertThrows(invalid, NumberFormatException.class, () -> index(evaluator, invalid));
    }
  }

  @Test public void nodeIndicesUseOwnedLexicalTextWithoutEvaluatingExpressions() throws Exception {
    var literal = P4PreferredAstMapper.parseDetailed("2", ExpressionTypes._float);
    var expression = P4PreferredAstMapper.parseDetailed("1+1", ExpressionTypes._float);
    var literalEvaluator = evaluator(literal.sourceText());
    if (hasOwnedSnapshotApi()) {
      assertEquals(2, index(literalEvaluator, literal.ast()));
      assertThrows(NumberFormatException.class, () -> index(evaluator(expression.sourceText()), expression.ast()));
      // Later mapping must not invalidate the first result or let foreign nodes reuse its positions.
      P4PreferredAstMapper.parseDetailed("12345", ExpressionTypes._float);
      assertEquals(2, index(literalEvaluator, literal.ast()));
      assertThrows(IllegalArgumentException.class, () -> index(literalEvaluator, expression.ast()));
    } else {
      assertThrows(IllegalArgumentException.class, () -> index(literalEvaluator, literal.ast()));
    }
    assertThrows(IllegalArgumentException.class, () -> index(evaluator(P4SourceText.lexicalOnly()), literal.ast()));
  }

  @Test public void suppliedOwnedNodeSpanPreservesSignAndCodePointCoordinatesOnBothGenerators() throws Exception {
    Object node = P4PreferredAstMapper.parseDetailed("2", ExpressionTypes._float).ast();
    var source = P4SourceText.fromSnapshot("😀 -2", value -> value == node
        ? Optional.of(new int[]{2, 4}) : Optional.empty());
    assertEquals(-2, index(evaluator(source), node));
    var expressionSource = P4SourceText.fromSnapshot("(2)", value -> value == node
        ? Optional.of(new int[]{0, 3}) : Optional.empty());
    assertThrows(NumberFormatException.class, () -> index(evaluator(expressionSource), node));
  }

  @Test public void sliceValuesKeepUtf16AndZeroStepSemantics() throws Exception {
    for (String[] sample : new String[][] {
        {"'abcdef'[1:4]", "bcd"}, {"'abcdef'[:3]", "abc"},
        {"'abcdef'[2:]", "cdef"}, {"'abcdef'[::-1]", "fedcba"},
        {"'abcdef'[::2]", "ace"}, {"'abcdef'[-3:-1]", "de"},
        {"'abcdef'[-99:99]", "abcdef"}, {"'abcdef'[4:2]", ""},
        {"'😀a'[1:2]", "\uDE00"}
    }) {
      var parsed = P4PreferredAstMapper.parseDetailed(sample[0], ExpressionTypes.string);
      P4PreferredAstMapper.parseDetailed("7", ExpressionTypes._float);
      assertEquals(sample[0], sample[1], evaluator(parsed.sourceText()).eval(parsed.ast()));
    }
    var zero = P4PreferredAstMapper.parseDetailed("'abcdef'[::0]", ExpressionTypes.string);
    var failure = assertThrows(IllegalArgumentException.class, () -> evaluator(zero.sourceText()).eval(zero.ast()));
    assertEquals("slice step cannot be zero", failure.getMessage());
  }

  @Test public void parsedAliasIndicesRetainTheirEntireLexicalExpression() throws Exception {
    String expectedNodeAlias = System.getProperty("tinyexpression.expected.mapper.nodeAlias");
    for (String[] sample : new String[][] {
        {"(1)", "(1)"}, {"(1+1)", "(1+1)"}, {"-2", "-2"},
        {"(1/*c*/)", "(1     )"}, {"1/*c*/+1", "1     +1"}
    }) {
      String formula = "/*😀*/ 'abcdef'[" + sample[0] + ":]";
      var parsed = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes.string);
      assertTrue(formula, parsed.ast() instanceof SliceExpr);
      Object start = captureValue(((SliceExpr) parsed.ast()).start());
      if (expectedNodeAlias != null) {
        if (Boolean.parseBoolean(expectedNodeAlias)) {
          assertTrue(formula + " must retain the generated semantic node", start instanceof TinyExpressionP4AST);
        } else {
          assertTrue(formula + " must exercise the published lexical API", start instanceof String);
        }
      }
      P4PreferredAstMapper.parseDetailed("9876", ExpressionTypes._float);
      assertEquals(formula, sample[1], parsed.sourceText().text(start).strip());
      var ownedEvaluator = evaluator(parsed.sourceText());
      if (sample[0].equals("-2")) {
        assertEquals(-2, index(ownedEvaluator, start));
        assertEquals("ef", ownedEvaluator.eval(parsed.ast()));
      } else {
        assertThrows(formula, NumberFormatException.class, () -> ownedEvaluator.eval(parsed.ast()));
      }
    }
  }

  @Test public void allThreeParsedIndicesUseTheDeclaredGeneratedContract() {
    String formula = "/*😀*/ 'abcdef'[1:5:2]";
    var parsed = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes.string);
    var slice = (SliceExpr) parsed.ast();
    String expected = System.getProperty("tinyexpression.expected.mapper.nodeAlias");
    Object[] indices = {
        captureValue(slice.start()), captureValue(slice.end()), captureValue(slice.step())};
    String[] lexical = {"1", "5", "2"};
    P4PreferredAstMapper.parseDetailed("9876", ExpressionTypes._float);
    for (int i = 0; i < indices.length; i++) {
      if (expected != null) {
        assertTrue("index " + i + " uses the wrong generator contract",
            Boolean.parseBoolean(expected) ? indices[i] instanceof TinyExpressionP4AST
                : indices[i] instanceof String);
      }
      assertEquals(lexical[i], parsed.sourceText().text(indices[i]).strip());
    }
    assertEquals("bd", evaluator(parsed.sourceText()).eval(slice));
  }

  @Test public void parsedNonIntegerAndOverflowIndicesKeepFailureClassesInEveryPosition() {
    for (String index : List.of("1.5", "2147483648")) {
      for (String suffix : List.of("[" + index + ":]", "[:" + index + "]", "[::" + index + "]")) {
        String formula = "'abcdef'" + suffix;
        var parsed = P4PreferredAstMapper.parseDetailed(formula, ExpressionTypes.string);
        P4PreferredAstMapper.parseDetailed("123", ExpressionTypes._float);
        assertThrows(formula, NumberFormatException.class, () -> evaluator(parsed.sourceText()).eval(parsed.ast()));
      }
    }
  }

  @Test public void detailedProbeAndCalculatorRetainParseResultUntilLaterEvaluation() {
    String formula = "/*😀*/ 'abcdef'[1:3]";
    var parsed = GeneratedAstRuntimeProbe.tryMapAstDetailed(formula,
        getClass().getClassLoader(), List.of("SliceExpr")).orElseThrow();
    var calculator = new AstEvaluatorCalculator(new Source(formula), "OwnedSlice",
        STRING_TYPES, getClass().getClassLoader());
    P4PreferredAstMapper.parseDetailed("9876", ExpressionTypes._float);
    assertEquals("bc", evaluator(parsed.sourceText()).eval(parsed.ast()));
    assertEquals("bc", calculator.apply(CalculationContext.newConcurrentContext()));
    assertEquals("p4-typed", calculator.getObject("_astEvaluatorRuntime", String.class));
  }

  @Test public void formulaAndMethodScopesKeepTheSameOwnedSource() {
    for (String formula : List.of("'abcdef'[1:3]", "call part() string part(){ 'abcdef'[1:3] }")) {
      var parsed = P4PreferredAstMapper.parseByAstSimpleNamesDetailed(formula, List.of("FormulaExpr"), 0L);
      P4PreferredAstMapper.parseDetailed("9876", ExpressionTypes._float);
      assertEquals("bc", evaluator(parsed.sourceText()).eval(parsed.ast()));
    }
  }
}
