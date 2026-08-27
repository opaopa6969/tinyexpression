package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * issue #11 §2 prep: P4PreferredAstMapper.preferredAstSimpleNames の precedence
 * list を pin する regression test。
 *
 * <p>facade 側の heuristic (match / if / ternary / 関数名 / dot method / slice)
 * を grammar metadata に寄せていく予定で、その slim 化作業中に precedence の
 * 並びが drift していないことを確認する。drift したら test が落ち、レビューで
 * 意図的な変更か事故かを切り分ける gate になる。
 *
 * <p>テストは facade を直接呼び、AST 解決まで踏み込まない。precedence list
 * そのものに対する契約だけを固定する。
 */
public class P4PreferredAstMapperPrecedenceTest {

    /** match expression は preferred result type に応じた *MatchExpr が先頭に来る。 */
    @Test
    public void matchExpressionWithNumberPreferredType() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "match{1==1->2,default->0}", ExpressionTypes._float);
        assertFalse("match precedence list should not be empty", names.isEmpty());
        assertEquals("number-preferred match must lead with NumberMatchExpr",
            "NumberMatchExpr", names.get(0));
    }

    @Test
    public void matchExpressionWithStringPreferredType() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "match{1==1->'a',default->'b'}", ExpressionTypes.string);
        assertEquals("string-preferred match must lead with StringMatchExpr",
            "StringMatchExpr", names.get(0));
    }

    @Test
    public void matchExpressionWithBooleanPreferredType() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "match{1==1->true,default->false}", ExpressionTypes._boolean);
        assertEquals("boolean-preferred match must lead with BooleanMatchExpr",
            "BooleanMatchExpr", names.get(0));
    }

    @Test
    public void matchExpressionFallbacksIncludeAllVariants() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "match{1==1->2,default->0}", ExpressionTypes._float);
        assertTrue("Fallback list should include NumberMatchExpr",
            names.contains("NumberMatchExpr"));
        assertTrue("Fallback list should include StringMatchExpr",
            names.contains("StringMatchExpr"));
        assertTrue("Fallback list should include BooleanMatchExpr",
            names.contains("BooleanMatchExpr"));
    }

    /** if (cond) { ... } else { ... } は IfExpr が先頭。 */
    @Test
    public void ifExpressionLeadsWithIfExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "if(true){1}else{0}", ExpressionTypes._float);
        assertEquals("if expression must lead with IfExpr", "IfExpr", names.get(0));
    }

    /** top-level ternary も IfExpr に正規化される。 */
    @Test
    public void ternaryExpressionAlsoMapsToIfExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "(true?1:0)", ExpressionTypes._float);
        assertTrue("ternary should include IfExpr in precedence",
            names.contains("IfExpr"));
    }

    /** 関数呼び出し → FUNCTION_AST_NAMES マップどおりの *Expr を返す。 */
    @Test
    public void sinFunctionPicksSinExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "sin(1)", ExpressionTypes._float);
        assertTrue("sin(...) should include SinExpr", names.contains("SinExpr"));
    }

    @Test
    public void isPresentFunctionPicksIsPresentExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "isPresent($x)", ExpressionTypes._boolean);
        assertTrue("isPresent should include IsPresentExpr",
            names.contains("IsPresentExpr"));
    }

    @Test
    public void inTimeRangeFunctionPicksInTimeRangeExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "inTimeRange(9,17)", ExpressionTypes._boolean);
        assertTrue("inTimeRange should include InTimeRangeExpr",
            names.contains("InTimeRangeExpr"));
    }

    /** dot method → DOT_METHOD_AST_NAMES どおり。 */
    @Test
    public void dotMethodToUpperCasePicksToUpperCaseDotExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "$s.toUpperCase()", ExpressionTypes.string);
        assertTrue("dot toUpperCase should include ToUpperCaseDotExpr",
            names.contains("ToUpperCaseDotExpr"));
    }

    /** slice expression → SliceExpr。 */
    @Test
    public void sliceExpressionPicksSliceExpr() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "$s[0:3]", ExpressionTypes.string);
        assertTrue("slice should include SliceExpr", names.contains("SliceExpr"));
    }

    /** null / 空文字列は空 list を返す (defensive contract)。 */
    @Test
    public void nullFormulaReturnsEmptyList() {
        assertTrue("null input must return empty",
            P4PreferredAstMapper.preferredAstSimpleNames(null, ExpressionTypes._float).isEmpty());
    }

    @Test
    public void emptyFormulaReturnsEmptyList() {
        assertTrue("empty input must return empty",
            P4PreferredAstMapper.preferredAstSimpleNames("", ExpressionTypes._float).isEmpty());
    }
}
