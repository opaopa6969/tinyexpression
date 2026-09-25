package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Golden tests for the language catalog (issue #201): the bundled catalog reproduces every text
 * the server showed before the catalog existed, and editing the catalog (an override file, as
 * the VSIX setting {@code tinyExpressionP4Lsp.catalog.overridePath} passes it) changes hover,
 * completion documentation and diagnostic messages.
 */
public class TinyExpressionP4CatalogProviderTest {

    private static final String URI = "file:///catalog-provider-test.tinyexp";

    private TinyExpressionP4LanguageServerExt server;
    private TinyExpressionP4LanguageServerExt.ExtTextDocumentService service;
    private final List<PublishDiagnosticsParams> published = new ArrayList<>();

    @Before
    public void setUp() {
        server = new TinyExpressionP4LanguageServerExt();
        service = (TinyExpressionP4LanguageServerExt.ExtTextDocumentService) server.getTextDocumentService();
        server.connect(new org.eclipse.lsp4j.services.LanguageClient() {
            @Override public void telemetryEvent(Object object) {}
            @Override public void publishDiagnostics(PublishDiagnosticsParams diagnostics) { published.add(diagnostics); }
            @Override public void showMessage(MessageParams messageParams) {}
            @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public void logMessage(MessageParams message) {}
        });
    }

    private static List<String> golden(String name) throws IOException {
        try (InputStream in = TinyExpressionP4CatalogProviderTest.class.getResourceAsStream("/catalog-golden/" + name)) {
            assertNotNull("golden " + name, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static String unescape(String text) {
        return text.replace("\\n", "\n").replace("\\\\", "\\");
    }

    // ── the bundled catalog lost nothing ──

    @Test
    public void bundledCatalogCountsMatchTheMigratedSources() {
        Map<String, Integer> counts = CatalogProvider.bundled().counts();
        // config/*.tecatalog: 344 + 201 + 739 + 87 lines (7 of them prefixWithSuffix).
        assertEquals(Integer.valueOf(1371), counts.get("variables"));
        assertEquals(Integer.valueOf(4), counts.get("variableGroups"));
        // TE001..TE025 (Java ERROR_CATALOG) + FI001/FI002 (FormulaInfo diagnostics).
        assertEquals(Integer.valueOf(27), counts.get("errorCodes"));
        // 26 function snippets + 8 dot-form methods.
        assertEquals(Integer.valueOf(34), counts.get("functions"));
        assertEquals(Integer.valueOf(23), counts.get("keywords"));
        assertEquals(Integer.valueOf(7), counts.get("values"));
        long prefixed = CatalogProvider.bundled().variables().stream()
            .filter(v -> "prefixWithSuffix".equals(v.match())).count();
        assertEquals(7, prefixed);
    }

    @Test
    public void everyTeCodeKeepsItsPre201FullMessage() throws IOException {
        List<String> expected = golden("te-full-messages.txt");
        assertEquals(25, expected.size());
        for (String line : expected) {
            String code = line.substring(1, line.indexOf(']'));
            assertEquals(line, CatalogProvider.bundled().errorCodes().get(code).fullMessage());
        }
    }

    @Test
    public void completionVocabularyKeepsItsPre201Tables() throws IOException {
        CatalogProvider catalog = CatalogProvider.bundled();
        List<String> keywords = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (String line : golden("completion-vocabulary.txt")) {
            String[] f = line.split("\t", 3);
            switch (f[0]) {
                case "keyword" -> keywords.add(f[1]);
                case "value" -> values.add(f[1]);
                case "function" -> assertEquals(f[1], unescape(f[2]), catalog.function(f[1]).snippet());
                case "block" -> assertEquals(f[1], unescape(f[2]), catalog.keywords().get(f[1]).snippet());
                default -> fail(line);
            }
        }
        assertEquals(keywords, List.copyOf(catalog.keywords().keySet()));
        assertEquals(values, catalog.values().stream().map(CatalogProvider.NamedValue::name).toList());
    }

    @Test
    public void legacyCodeRulesStillPickTheSameCodes() {
        CatalogProvider catalog = CatalogProvider.bundled();
        assertEquals("TE006", catalog.resolveCode("Expected ';'", "", ""));
        assertEquals("TE004", catalog.resolveCode("Expected ')'", "", ""));
        assertEquals("TE005", catalog.resolveCode("Expected '}'", "", ""));
        assertEquals("TE017", catalog.resolveCode("Expected 'var'", "", ""));
        assertEquals("TE023", catalog.resolveCode("Unexpected &&", "", ""));
        assertEquals("TE011", catalog.resolveCode("", "if $x", ""));
        assertEquals("TE011", catalog.resolveCode("", "", "x if "));
        assertEquals("TE002", catalog.resolveCode("", "abc", ""));
        assertEquals("TE020", catalog.resolveCode("", "1 + +", ""));
        // A playground-only rule does not change the LSP.
        assertEquals("TE020", catalog.resolveCode("", "&& $b", ""));
    }

    @Test
    public void prefixWithSuffixVariablesResolveByPrefix() {
        CatalogProvider catalog = CatalogProvider.bundled();
        CatalogProvider.Variable v = catalog.lookupVariable("$acceptParams_userAgent");
        assertNotNull(v);
        assertEquals("acceptParams", v.name());
        assertNull(catalog.lookupVariable("acceptParams_"));
        assertNotNull(catalog.variableResolver().lookup("ForcedRelativeSuspiciousValue1"));
    }

    // ── the server reads the catalog ──

    private InitializeParams initializeWith(Map<String, Object> options) {
        InitializeParams params = new InitializeParams();
        params.setInitializationOptions(options);
        return params;
    }

    private String hoverAt(String content, int character) throws Exception {
        server.parseDocument(URI, content);
        HoverParams params = new HoverParams(new TextDocumentIdentifier(URI), new Position(0, character));
        return service.hover(params).get().getContents().getRight().getValue();
    }

    private CompletionItem completionItem(String content, String label) throws Exception {
        server.parseDocument(URI, content);
        CompletionParams params = new CompletionParams(new TextDocumentIdentifier(URI), new Position(0, content.length()));
        return service.completion(params).get().getLeft().stream()
            .filter(i -> label.equals(i.getLabel())).findFirst().orElse(null);
    }

    private Diagnostic diagnostic(String code) {
        List<Diagnostic> last = published.get(published.size() - 1).getDiagnostics();
        return last.stream()
            .filter(d -> d.getCode() != null && d.getCode().isLeft() && code.equals(d.getCode().getLeft()))
            .findFirst().orElse(null);
    }

    @Test
    public void bundledCatalogFeedsHoverCompletionAndDiagnostics() throws Exception {
        server.initialize(initializeWith(Map.of("useBundledVariables", true))).get();

        String sqrt = hoverAt("sqrt(16)", 1);
        assertTrue(sqrt, sqrt.contains("sqrt(x: number): number"));
        assertTrue(sqrt, sqrt.contains("平方根"));

        String variable = hoverAt("$ForcedRelativeSuspiciousValue1", 3);
        assertTrue(variable, variable.contains("BOOLEAN (FA variable)"));

        CompletionItem item = completionItem("sq", "sqrt");
        assertNotNull(item);
        assertEquals("sqrt($1)$0", item.getInsertText());
        assertTrue(item.getDocumentation().getRight().getValue().contains("平方根"));

        server.parseDocument(URI, "$ForcedRelativeSuspiciousValue1 & $notInAnyCatalog");
        Diagnostic te022 = diagnostic("TE022");
        assertNotNull(te022);
        assertEquals("[TE022] 利用可能な変数名ではありません。 修正例: 候補変数名へ修正"
            + " (catalog 未登録の変数: $notInAnyCatalog)", te022.getMessage());
    }

    @Test
    public void editingTheCatalogChangesHoverCompletionAndDiagnostics() throws Exception {
        Path override = Files.createTempFile("tinyexpression-catalog-override", ".json");
        try {
            Files.writeString(override, """
                {
                  "functions": [
                    {"name": "sqrt", "kind": "function", "signature": "sqrt(x: number): number",
                     "returns": "number", "snippet": "sqrt($1)$0",
                     "description": {"ja": "編集した平方根の説明"}, "examples": ["sqrt(81)"]}
                  ],
                  "variables": [
                    {"name": "riskScore", "match": "exact", "type": "float",
                     "description": {"ja": "playground で追加したリスクスコア"}, "context": "TEST", "group": "playground"}
                  ],
                  "errorCodes": [
                    {"code": "TE022", "severity": "warning", "message": {"ja": "カタログに無い変数です。"},
                     "fix": {"ja": "カタログへ追加"},
                     "templates": {"UNKNOWN_VARIABLE": {"ja": "{fullMessage} [{symbol}]"}}},
                    {"code": "FI002", "severity": "error", "message": {"ja": "x"}, "fix": {"ja": "x"},
                     "templates": {"DEFAULT": {"ja": "[FI002] 未知のバックエンド '{value}'"}}}
                  ]
                }
                """);
            server.initialize(initializeWith(Map.of(
                "catalogOverridePath", override.toString(),
                "useBundledVariables", true))).get();

            String sqrt = hoverAt("sqrt(16)", 1);
            assertTrue(sqrt, sqrt.contains("編集した平方根の説明"));
            assertFalse(sqrt, sqrt.contains("平方根。"));
            assertTrue(sqrt, sqrt.contains("sqrt(81)"));

            String added = hoverAt("$riskScore", 3);
            assertTrue(added, added.contains("playground で追加したリスクスコア"));

            CompletionItem item = completionItem("sq", "sqrt");
            assertTrue(item.getDocumentation().getRight().getValue().contains("編集した平方根の説明"));

            server.parseDocument(URI, "$riskScore + $unknownVariable");
            Diagnostic te022 = diagnostic("TE022");
            assertNotNull(te022);
            assertEquals("[TE022] カタログに無い変数です。 修正例: カタログへ追加 [$unknownVariable]", te022.getMessage());

            server.parseDocument(URI, "calculatorName:a\nexecutionBackend:P4_MAGIC\nformula:\n1\n---END_OF_PART---\n");
            assertEquals("[FI002] 未知のバックエンド 'P4_MAGIC'", diagnostic("FI002").getMessage());

            // Untouched entries keep the bundled text.
            assertEquals(CatalogProvider.bundled().errorCodes().get("TE006").fullMessage(),
                server.catalog().errorCodes().get("TE006").fullMessage());
        } finally {
            Files.deleteIfExists(override);
        }
    }

    /**
     * Stage 4 of issue #201: the override the playground's Catalog panel exports (the golden
     * fixture is regenerated and checked by playground/scripts/catalog-roundtrip.mjs from the
     * panel's own editing model), imported with "TinyExpression: Import catalog from playground
     * export" (which sets catalog.overridePath), changes hover, completion and diagnostic texts.
     */
    @Test
    public void anImportedPlaygroundOverrideChangesHover() throws Exception {
        Path override = Path.of(getClass().getResource("/catalog-golden/playground-export.override.json").toURI());
        server.initialize(initializeWith(Map.of(
            "catalogOverridePath", override.toString(),
            "useBundledVariables", true))).get();

        String sqrt = hoverAt("sqrt(16)", 1);
        assertTrue(sqrt, sqrt.contains("playground で編集した平方根の説明"));
        assertTrue(sqrt, sqrt.contains("sqrt(81)"));
        assertFalse(sqrt, sqrt.contains("平方根。負数は NaN になります。"));
        String added = hoverAt("$riskScore", 3);
        assertTrue(added, added.contains("playground で追加したリスクスコア"));
        CompletionItem item = completionItem("sq", "sqrt");
        assertTrue(item.getDocumentation().getRight().getValue().contains("playground で編集した平方根の説明"));
        assertEquals("[TE006] 文末のセミコロンが必要です。 修正例: 行末に ; を付ける（playground で編集）",
            server.catalog().errorCodes().get("TE006").fullMessage());
        // Everything the export did not touch keeps the bundled text.
        assertEquals(CatalogProvider.bundled().errorCodes().get("TE004").fullMessage(),
            server.catalog().errorCodes().get("TE004").fullMessage());
        assertTrue(server.catalog().origin(), server.catalog().origin().endsWith("playground-export.override.json"));
    }

    /** A full catalog exported from the playground works as an override too. */
    @Test
    public void anImportedFullPlaygroundExportChangesHover() throws Exception {
        String bundled;
        try (InputStream in = CatalogProvider.class.getResourceAsStream("/tinyexpression-catalog.json")) {
            bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(bundled.contains("\"平方根。負数は NaN になります。\""));
        Path full = Files.createTempFile("tinyexpression-catalog-full-export", ".json");
        try {
            Files.writeString(full, bundled.replace("\"平方根。負数は NaN になります。\"", "\"全体書き出しで編集した平方根\""));
            server.initialize(initializeWith(Map.of("catalogOverridePath", full.toString(), "useBundledVariables", true))).get();
            String sqrt = hoverAt("sqrt(16)", 1);
            assertTrue(sqrt, sqrt.contains("全体書き出しで編集した平方根"));
            assertEquals(CatalogProvider.bundled().variables().size(), server.catalog().variables().size());
        } finally {
            Files.deleteIfExists(full);
        }
    }

    @Test
    public void aJsonCatalogPathIsReadAsAnOverride() throws Exception {
        Path override = Files.createTempFile("tinyexpression-catalog-path", ".json");
        try {
            Files.writeString(override, """
                {"variables": [{"name": "onlyHere", "match": "exact", "description": {"ja": "ここだけの変数"},
                  "context": "TEST", "group": "t"}]}
                """);
            server.initialize(initializeWith(Map.of("catalogPath", override.toString()))).get();
            assertTrue(hoverAt("$onlyHere", 3).contains("ここだけの変数"));
        } finally {
            Files.deleteIfExists(override);
        }
    }

    @Test
    public void withoutOptionsVariablesStayOff() throws Exception {
        server.initialize(initializeWith(Map.of())).get();
        server.parseDocument(URI, "$anything + 1");
        assertNull("no variable catalog configured: no TE022", diagnostic("TE022"));
    }
}
