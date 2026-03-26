package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4LanguageServer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Phase 3 integration tests: CatalogResolver completion and hover support.
 */
public class TinyExpressionP4CatalogIntegrationTest {

    private TinyExpressionP4LanguageServerExt server;
    private TinyExpressionP4LanguageServerExt.ExtTextDocumentService service;
    private final String TEST_URI = "file:///catalog-test.tinyexp";

    @Before
    public void setUp() {
        server = new TinyExpressionP4LanguageServerExt();
        service = (TinyExpressionP4LanguageServerExt.ExtTextDocumentService) server.getTextDocumentService();
    }

    /**
     * Set up an in-memory CatalogResolver with test entries.
     */
    private void installTestCatalog() {
        server.setCatalogResolver(new TinyExpressionP4LanguageServer.CatalogResolver() {
            private final List<TinyExpressionP4LanguageServer.CatalogEntry> entries = List.of(
                new TinyExpressionP4LanguageServer.CatalogEntry(
                    "ForcedRelativeSuspiciousValue1", "強制相対不審スコア1", "variable", "test"),
                new TinyExpressionP4LanguageServer.CatalogEntry(
                    "riskScore", "リスクスコア（数値）", "variable", "test"),
                new TinyExpressionP4LanguageServer.CatalogEntry(
                    "inputName", "入力名称", "variable", "test")
            );
            @Override public List<TinyExpressionP4LanguageServer.CatalogEntry> listAll() { return entries; }
            @Override public TinyExpressionP4LanguageServer.CatalogEntry lookup(String name) {
                return entries.stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
            }
        });
    }

    @Test
    public void testCatalogCompletionReturnsAllEntries() throws Exception {
        installTestCatalog();
        String content = "$";
        server.parseAndEnrich(TEST_URI, content, 0);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 1)); // after $

        List<CompletionItem> items = service.completion(params).get().getLeft();

        boolean foundCatalogEntry = items.stream()
            .anyMatch(item -> "$ForcedRelativeSuspiciousValue1".equals(item.getLabel()));
        assertTrue("Should suggest catalog variable $ForcedRelativeSuspiciousValue1", foundCatalogEntry);

        boolean foundRiskScore = items.stream()
            .anyMatch(item -> "$riskScore".equals(item.getLabel()));
        assertTrue("Should suggest catalog variable $riskScore", foundRiskScore);
    }

    @Test
    public void testCatalogCompletionWithPrefix() throws Exception {
        installTestCatalog();
        String content = "$risk";
        server.parseAndEnrich(TEST_URI, content, 0);

        CompletionParams params = new CompletionParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 5)); // after "$risk"

        List<CompletionItem> items = service.completion(params).get().getLeft();

        boolean foundRiskScore = items.stream()
            .anyMatch(item -> "$riskScore".equals(item.getLabel()));
        assertTrue("Should suggest $riskScore when prefix is $risk", foundRiskScore);
    }

    @Test
    public void testCatalogHoverReturnsDescription() throws Exception {
        installTestCatalog();
        String content = "$ForcedRelativeSuspiciousValue1";
        server.parseAndEnrich(TEST_URI, content, 0);

        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 5)); // somewhere in the variable name

        Hover hover = service.hover(params).get();
        assertNotNull("Hover should not be null", hover);
        assertNotNull("Hover content should not be null", hover.getContents());
        String value = hover.getContents().getRight().getValue();
        assertTrue("Hover should contain catalog description",
            value.contains("強制相対不審スコア1"));
    }

    @Test
    public void testNoCatalogHoverWhenResolverEmpty() throws Exception {
        // No catalog installed — hover falls back to parse status
        String content = "$someVar";
        server.parseAndEnrich(TEST_URI, content, 0);

        HoverParams params = new HoverParams();
        params.setTextDocument(new TextDocumentIdentifier(TEST_URI));
        params.setPosition(new Position(0, 3));

        // Should not throw; returns parse status hover
        Hover hover = service.hover(params).get();
        // Result can be null or contain parse status — just verify no exception
        // (no catalog installed, so catalog hover returns null, falls back gracefully)
    }
}
