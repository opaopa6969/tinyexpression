package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TinyExpressionCompatibilityParseTest {

    @Test
    void parsesIfElseMatchFormulaWithoutRuntimeLinkErrors() {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        String expression = """
                import CheckDigits#check as checkDigits;
                var $input as string set if not exists 'not number' description='input';
                if(external returning as boolean checkDigits($input)){
                  1
                }else{
                  match{
                    true -> 1 ,
                    $didCheck -> 2,
                    default -> 0
                  }
                }
                """;

        CalculatorLanguageServer.ParseResult result = server.parseExpression(expression);

        assertTrue(result.succeeded, "parse should succeed without linkage errors");
        assertEquals(result.totalLength, result.consumedLength, "formula should be fully consumed");
    }
}
