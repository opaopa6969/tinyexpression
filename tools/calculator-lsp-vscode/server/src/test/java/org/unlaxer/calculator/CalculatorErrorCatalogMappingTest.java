package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

class CalculatorErrorCatalogMappingTest {

    @Test
    void mapsIfMissingClosingParenthesisToTe011() throws Exception {
        String expression = "if(true {1}else{0}";
        assertEquals("TE011", resolveCatalogCode(expression));
    }

    @Test
    void mapsIfEmptyConditionToTe011AndVarMissingSemicolonToTe006() throws Exception {
        String expression = "if(){1}else{0}";
        assertEquals("TE011", resolveCatalogCode(expression));
        String missingSemicolon = "var $input as string set if not exists 'n'\nif(true){1}else{0}";
        assertEquals("TE006", resolveCatalogCode(missingSemicolon));
    }

    private String resolveCatalogCode(String content) throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        CalculatorLanguageServer.ParseResult result = server.parseExpression(content);
        assertFalse(result.succeeded, "expression should fail parsing in this test");

        Method describe = CalculatorLanguageServer.class.getDeclaredMethod(
                "describeParseFailure", String.class, int.class, Object.class);
        describe.setAccessible(true);
        Object failure = describe.invoke(
                server,
                content,
                Math.max(0, result.consumedLength),
                result.failureDiagnostics);

        Class<?> failureClass = Class.forName(
                "org.unlaxer.calculator.CalculatorLanguageServer$ParseFailureDescription");
        Method resolve = CalculatorLanguageServer.class.getDeclaredMethod(
                "resolveErrorCatalogEntry", String.class, failureClass);
        resolve.setAccessible(true);
        Object entry = resolve.invoke(server, content, failure);

        Method code = entry.getClass().getDeclaredMethod("code");
        code.setAccessible(true);
        return String.valueOf(code.invoke(entry));
    }
}
