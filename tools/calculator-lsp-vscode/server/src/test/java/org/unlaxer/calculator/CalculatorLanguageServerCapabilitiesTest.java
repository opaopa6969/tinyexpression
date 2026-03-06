package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.junit.jupiter.api.Test;

class CalculatorLanguageServerCapabilitiesTest {

    @Test
    void initializeAdvertisesStructuredCodeActionKinds() throws Exception {
        CalculatorLanguageServer server = new CalculatorLanguageServer();
        InitializeResult result = server.initialize(new InitializeParams()).get();
        assertNotNull(result);

        ServerCapabilities capabilities = result.getCapabilities();
        assertNotNull(capabilities);
        assertNotNull(capabilities.getCodeActionProvider());
        assertTrue(capabilities.getCodeActionProvider().isRight());

        CodeActionOptions options = capabilities.getCodeActionProvider().getRight();
        assertNotNull(options);
        assertFalse(Boolean.TRUE.equals(options.getResolveProvider()));
        List<String> kinds = options.getCodeActionKinds();
        assertNotNull(kinds);
        assertTrue(kinds.contains(CodeActionKind.QuickFix));
        assertTrue(kinds.contains(CodeActionKind.QuickFix + ".rewrite"));
        assertTrue(kinds.contains(CodeActionKind.QuickFix + ".insert"));
        assertEquals(3, kinds.size());
    }
}
