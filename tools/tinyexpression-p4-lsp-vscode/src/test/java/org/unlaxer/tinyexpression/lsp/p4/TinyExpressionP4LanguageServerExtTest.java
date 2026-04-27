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
    public void testStrictMatchTypingDiagnosticForBareVariable() throws Exception {
        CapturingLanguageClient client = new CapturingLanguageClient();
        server.connect(client);

        server.parseDocument(TEST_URI, "match{1==1->$val,default->0}");

        Diagnostic diag = client.firstDiagnosticWithCode("TE025");
        assertNotNull("TE025 diagnostic should be published", diag);
        assertTrue(diag.getMessage().contains("inline type hint"));

        Hover hover = service.hover(hoverParams(0, 0)).get();
        assertNotNull(hover);
        assertNotNull(hover.getContents().getRight());
        assertTrue(hover.getContents().getRight().getValue().contains("TE025"));
    }

    @Test
    public void testStrictMatchTypingAllowsHintedVariable() {
        CapturingLanguageClient client = new CapturingLanguageClient();
        server.connect(client);

        server.parseDocument(TEST_URI, "match{1==1->$val as number,default->0}");

        assertFalse("TE025 should not be published for hinted variable", client.hasDiagnosticCode("TE025"));
    }

    @Test
    public void testHoverShowsPreferredMatchAstRoot() throws Exception {
        CapturingLanguageClient client = new CapturingLanguageClient();
        server.connect(client);

        server.parseDocument(TEST_URI, "match{1==1->$val as number,default->0}");

        Hover hover = service.hover(hoverParams(0, 0)).get();
        assertNotNull(hover);
        assertNotNull(hover.getContents().getRight());
        assertTrue(hover.getContents().getRight().getValue().contains("NumberMatchExpr"));
    }

    @Test
    public void testStrictMatchTypingDiagnosticForParenthesizedBareVariable() {
        CapturingLanguageClient client = new CapturingLanguageClient();
        server.connect(client);

        server.parseDocument(TEST_URI, "match{1==1->($val),default->0}");

        Diagnostic diag = client.firstDiagnosticWithCode("TE025");
        assertNotNull("TE025 diagnostic should be published for parenthesized bare variable", diag);
        assertTrue(diag.getMessage().contains("inline type hint"));
    }

    @Test
    public void testStrictMatchTypingDiagnosticForDirectMethodInvocation() {
        CapturingLanguageClient client = new CapturingLanguageClient();
        server.connect(client);

        server.parseDocument(TEST_URI, "match{1==1->internal score(),default->0}");

        Diagnostic diag = client.firstDiagnosticWithCode("TE025");
        assertNotNull("TE025 diagnostic should be published", diag);
        assertTrue(diag.getMessage().contains("method invocation"));
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

    /**
     * issue #11 §3 kickoff: "$" trigger completion. With "$" registered as a
     * completion trigger character (CompletionOptions.setTriggerCharacters), the
     * client invokes textDocument/completion when the user types "$". The
     * existing prefix path already returns variables starting with "$" — this
     * test guards against accidentally regressing that path or the trigger
     * registration.
     */
    @Test
    public void testCompletionDollarTrigger() throws Exception {
        String content = "var $amount as number = 100;\n$";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(1, 1)); // right after the "$" on line 1

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = service.completion(params);
        List<CompletionItem> items = result.get().getLeft();

        boolean foundAmount = items.stream().anyMatch(item -> "$amount".equals(item.getLabel()));
        assertTrue("$amount should be suggested after $ trigger", foundAmount);
    }

    /**
     * Function snippet completion — paren auto-completion. issue #11 §3
     * "括弧補完" の最小実装。typing "si" should suggest "sin" with snippet
     * insert text "sin($1)$0".
     */
    @Test
    public void testCompletionFunctionSnippet() throws Exception {
        String content = "si";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 2)); // right after "si"

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = service.completion(params);
        List<CompletionItem> items = result.get().getLeft();

        CompletionItem sin = items.stream()
            .filter(i -> "sin".equals(i.getLabel()))
            .findFirst()
            .orElse(null);
        assertNotNull("sin function snippet should be suggested", sin);
        assertEquals(CompletionItemKind.Function, sin.getKind());
        assertEquals(InsertTextFormat.Snippet, sin.getInsertTextFormat());
        assertEquals("sin($1)$0", sin.getInsertText());
    }

    /**
     * Block-keyword snippet completion — semicolon completion 由来。issue #11
     * §3 セミコロン補完を declaration テンプレート経由で復元する。"var" を
     * 受け入れると `;` までが入った宣言テンプレートが展開される。
     */
    @Test
    public void testCompletionBlockKeywordSnippet() throws Exception {
        String content = "va";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 2));

        CompletableFuture<Either<List<CompletionItem>, CompletionList>> result = service.completion(params);
        List<CompletionItem> items = result.get().getLeft();

        CompletionItem snippet = items.stream()
            .filter(i -> "var".equals(i.getLabel()) && i.getKind() == CompletionItemKind.Snippet)
            .findFirst()
            .orElse(null);
        assertNotNull("var snippet should be suggested", snippet);
        assertEquals(InsertTextFormat.Snippet, snippet.getInsertTextFormat());
        assertTrue("var snippet should terminate with ;",
            snippet.getInsertText().contains(";"));
        assertTrue("var snippet should reference $name placeholder",
            snippet.getInsertText().contains("\\$${1:name}"));
    }

    /**
     * Trigger characters are exposed via ServerCapabilities. The P4 LSP must
     * register at least "$" so VS Code auto-invokes completion on "$".
     */
    @Test
    public void testCompletionTriggerCharactersIncludeDollar() throws Exception {
        InitializeParams init = new InitializeParams();
        init.setProcessId(0);
        init.setCapabilities(new ClientCapabilities());
        InitializeResult res = server.initialize(init).get();
        CompletionOptions co = res.getCapabilities().getCompletionProvider();
        assertNotNull("CompletionProvider must be advertised", co);
        assertNotNull("Trigger characters must be set", co.getTriggerCharacters());
        assertTrue("\"$\" must be a completion trigger character",
            co.getTriggerCharacters().contains("$"));
    }

    /**
     * issue #11 §3 「クイックフィックス群が薄い」の解消。TE006 (missing ;) /
     * TE004 (missing )) / TE005 (missing }) / TE002 (bare identifier) の
     * insert quick fix を確認する。
     */
    @Test
    public void testQuickFixTE006InsertSemicolon() throws Exception {
        String content = "var $a as number set 1 description='x'";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        Diagnostic diag = new Diagnostic();
        diag.setCode(Either.forLeft("TE006"));
        Position end = new Position(0, content.length());
        diag.setRange(new Range(end, end));
        diag.setMessage("[TE006] ;");

        CodeActionContext ctx = new CodeActionContext(List.of(diag));
        CodeActionParams params = new CodeActionParams(
            new TextDocumentIdentifier(TEST_URI), diag.getRange(), ctx);

        var actions = service.codeAction(params).get();
        boolean found = actions.stream()
            .filter(Either::isRight)
            .map(Either::getRight)
            .anyMatch(ca -> ca.getTitle().contains("Insert ';'"));
        assertTrue("Should offer 'Insert ;' quick fix for TE006", found);
    }

    @Test
    public void testQuickFixTE004InsertCloseParen() throws Exception {
        String content = "(1+2";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        Diagnostic diag = new Diagnostic();
        diag.setCode(Either.forLeft("TE004"));
        Position end = new Position(0, content.length());
        diag.setRange(new Range(end, end));
        diag.setMessage("[TE004] )");

        CodeActionContext ctx = new CodeActionContext(List.of(diag));
        CodeActionParams params = new CodeActionParams(
            new TextDocumentIdentifier(TEST_URI), diag.getRange(), ctx);

        var actions = service.codeAction(params).get();
        boolean found = actions.stream()
            .filter(Either::isRight)
            .map(Either::getRight)
            .anyMatch(ca -> ca.getTitle().contains("Insert ')'"));
        assertTrue("Should offer 'Insert )' quick fix for TE004", found);
    }

    @Test
    public void testQuickFixTE005InsertCloseBrace() throws Exception {
        String content = "if (true) {1";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        Diagnostic diag = new Diagnostic();
        diag.setCode(Either.forLeft("TE005"));
        Position end = new Position(0, content.length());
        diag.setRange(new Range(end, end));
        diag.setMessage("[TE005] }");

        CodeActionContext ctx = new CodeActionContext(List.of(diag));
        CodeActionParams params = new CodeActionParams(
            new TextDocumentIdentifier(TEST_URI), diag.getRange(), ctx);

        var actions = service.codeAction(params).get();
        boolean found = actions.stream()
            .filter(Either::isRight)
            .map(Either::getRight)
            .anyMatch(ca -> ca.getTitle().contains("Insert '}'"));
        assertTrue("Should offer 'Insert }' quick fix for TE005", found);
    }

    @Test
    public void testQuickFixTE002PrefixDollar() throws Exception {
        String content = "amount";
        server.parseAndEnrich(TEST_URI, content, 0, content);

        Diagnostic diag = new Diagnostic();
        diag.setCode(Either.forLeft("TE002"));
        Position start = new Position(0, 0);
        diag.setRange(new Range(start, start));
        diag.setMessage("[TE002] bare identifier");

        CodeActionContext ctx = new CodeActionContext(List.of(diag));
        CodeActionParams params = new CodeActionParams(
            new TextDocumentIdentifier(TEST_URI), diag.getRange(), ctx);

        var actions = service.codeAction(params).get();
        boolean found = actions.stream()
            .filter(Either::isRight)
            .map(Either::getRight)
            .anyMatch(ca -> ca.getTitle().contains("Prefix '$'"));
        assertTrue("Should offer 'Prefix $' quick fix for TE002", found);
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

    private HoverParams hoverParams(int line, int character) {
        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(line, character));
        return params;
    }

    private static class CapturingLanguageClient extends DummyLanguageClient {
        private List<Diagnostic> lastDiagnostics = List.of();

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            lastDiagnostics = diagnostics.getDiagnostics();
        }

        boolean hasDiagnosticCode(String code) {
            return firstDiagnosticWithCode(code) != null;
        }

        Diagnostic firstDiagnosticWithCode(String code) {
            return lastDiagnostics.stream()
                .filter(diag -> diag.getCode() != null)
                .filter(diag -> diag.getCode().isLeft())
                .filter(diag -> code.equals(diag.getCode().getLeft()))
                .findFirst()
                .orElse(null);
        }
    }
}
