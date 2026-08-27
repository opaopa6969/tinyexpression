package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * P4PreferredAstMapper の generated-AST candidate contract を pin する regression test。
 *
 * <p>候補はソース文字列の手書き走査ではなく、呼び出し側が指定した
 * 結果型だけから決定的に作る。実際のルート選択は生成 mapper が行い、
 * 式全体を覆う AST だけが採用される。
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
    public void typedCandidatesDoNotTryOtherMatchFamilies() {
        List<String> names = P4PreferredAstMapper.preferredAstSimpleNames(
            "match{1==1->2,default->0}", ExpressionTypes._float);
        assertTrue(names.contains("NumberMatchExpr"));
        assertFalse(names.contains("StringMatchExpr"));
        assertFalse(names.contains("BooleanMatchExpr"));
    }

    /** ソース形状で候補順を変えない。 */
    @Test
    public void candidateOrderDependsOnResultTypeNotSourceShape() {
        List<String> ifNames = P4PreferredAstMapper.preferredAstSimpleNames(
            "if(true){1}else{0}", ExpressionTypes._float);
        List<String> arithmeticNames = P4PreferredAstMapper.preferredAstSimpleNames(
            "1+2", ExpressionTypes._float);
        assertEquals(arithmeticNames, ifNames);
        assertTrue(ifNames.contains("IfExpr"));
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
