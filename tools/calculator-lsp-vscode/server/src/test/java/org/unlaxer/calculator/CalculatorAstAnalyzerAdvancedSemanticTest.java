package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.unlaxer.Parsed;

class CalculatorAstAnalyzerAdvancedSemanticTest {

    @Test
    void emitsTe011ForClearlyNonBooleanIfCondition() {
        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer();
        String content = "if(1){1}else{0}";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        long te011Count = errors.stream().filter(error -> error.message().startsWith("[TE011]")).count();
        assertEquals(1, te011Count);
    }

    @Test
    void doesNotEmitTe011ForComparisonIfCondition() {
        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer();
        String content = "if($x==1){1}else{0}";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        long te011Count = errors.stream().filter(error -> error.message().startsWith("[TE011]")).count();
        assertEquals(0, te011Count);
    }

    @Test
    void emitsTe015ForInvalidMinMaxArity() {
        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer();
        String content = "min(1,2,3) + max(1)";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        long te015Count = errors.stream().filter(error -> error.message().startsWith("[TE015]")).count();
        assertTrue(te015Count >= 2, "should report both invalid min/max arity calls");
    }

    @Test
    void doesNotEmitTe015ForNestedTwoArgumentMinMax() {
        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer();
        String content = "max(1,min(2,3))";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        long te015Count = errors.stream().filter(error -> error.message().startsWith("[TE015]")).count();
        assertEquals(0, te015Count);
    }
}
