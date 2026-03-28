package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TinyExpressionP4LanguageServerExtTest {

    private TinyExpressionP4LanguageServerExt server;
    private TinyExpressionP4LanguageServerExt.ExtTextDocumentService service;
    private final String TEST_URI = "file:///test.tinyexp";

    @Before
    public void setUp() {
        server = new TinyExpressionP4LanguageServerExt();
        service = (TinyExpressionP4LanguageServerExt.ExtTextDocumentService) server.getTextDocumentService();
    }

    @Test
    public void testDiagnosticsTE011() {
        final boolean[] called = {false};
        server.connect(new DummyLanguageClient() {
            @Override
            public void publishDiagnostics(PublishDiagnosticsParams params) {
                if (params.getDiagnostics().isEmpty()) return;
                called[0] = true;
                Diagnostic diag = params.getDiagnostics().get(0);
                // "if $x else $y" matches resolveCode for "'if'" -> TE011
                assertEquals("TE011", diag.getCode().getLeft());
                assertTrue(diag.getMessage().contains("if 条件には booleanExpression が必要です"));
            }
        });
        server.parseDocument(TEST_URI, "if $x else $y");
        assertTrue("Diagnostics should be published", called[0]);
    }

    @Test
    public void testCompletion() throws Exception {
        // Use MethodDeclaration to check ScopeStore completion
        String content = "10\nnumber myMethod($p) { $p }";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(1, 7)); // cursor on 'm' of "myMethod" → prefix="myMethod"

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = service.completion(params);
        List<CompletionItem> items = result.get().getLeft();

        boolean found = items.stream().anyMatch(item -> "myMethod".equals(item.getLabel()));
        assertTrue("Should suggest myMethod", found);
    }

    @Test
    public void testFormatting() throws Exception {
        String unformatted = "if($x){\ncall func();\n}else{\ncall other();\n}";
        server.parseDocument(TEST_URI, unformatted);

        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setOptions(new FormattingOptions(2, true));

        List<? extends TextEdit> edits = service.formatting(params).get();
        assertFalse(edits.isEmpty());
        String formatted = edits.get(0).getNewText();
        assertTrue(formatted.contains("  call func();"));
    }

    @Test
    public void testDefinition() throws Exception {
        // Variable declaration then reference
        String content = "var $x description = 'd';\n$x";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        DefinitionParams params = new DefinitionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(1, 1)); // on $x in second line

        var result = service.definition(params).get();
        
        if (result.isRight()) {
            assertFalse("Definition links should not be empty", result.getRight().isEmpty());
            assertEquals(0, result.getRight().get(0).getTargetRange().getStart().getLine());
        } else {
            assertFalse("Definition locations should not be empty", result.getLeft().isEmpty());
            assertEquals(0, result.getLeft().get(0).getRange().getStart().getLine());
        }
    }

    @Test
    public void testLinkedEditingRange() throws Exception {
        String content = "var $x description = 'd';\n$x";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        LinkedEditingRangeParams params = new LinkedEditingRangeParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 5)); // on $x in var $x

        LinkedEditingRanges result = service.linkedEditingRange(params).get();
        assertNotNull(result);
        assertFalse("Should find linked ranges", result.getRanges().isEmpty());
    }

    private static class DummyLanguageClient implements org.eclipse.lsp4j.services.LanguageClient {
        @Override public void telemetryEvent(Object object) {}
        @Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {}
        @Override public void showMessage(MessageParams messageParams) {}
        @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams showMessageRequestParams) { return null; }
        @Override public void logMessage(MessageParams message) {}
    }
}
