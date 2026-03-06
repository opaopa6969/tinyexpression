package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.unlaxer.Parsed;

class TinyExpressionVariableCatalogTest {

    @Test
    void loadsLegacyAndCanonicalCatalogFormats() throws Exception {
        Path legacyCatalog = Files.createTempFile("tinyexpression-legacy-catalog", ".txt");
        Files.writeString(legacyCatalog, String.join("\n",
                "# variable|type|api|description",
                "kind_*|string|catalog|partial key",
                "$direct_*|string|catalog|partial key",
                "plainName|string|catalog|exact",
                "checkKindLike|All|チェック種別の説明",
                "typeOnly|string"));
        Path canonicalCatalog = Files.createTempFile("tinyexpression-canonical-catalog", ".tecatalog");
        Files.writeString(canonicalCatalog, String.join("\n",
                "# canonical",
                "tinyexpression-catalog-v1",
                "exact|score|score description|nimt",
                "prefixWithSuffix|segment|_|1|segment description|fa"));

        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(
                        legacyCatalog + "," + canonicalCatalog,
                        "test");

        assertTrue(rules.partialPrefixes().contains("kind"));
        assertTrue(rules.partialPrefixes().contains("direct"));
        assertTrue(rules.partialPrefixes().contains("segment"));
        assertTrue(rules.exactNames().contains("plainName"));
        assertTrue(rules.exactNames().contains("score"));
        assertTrue(rules.exactNames().contains("checkKindLike"));
        assertTrue(rules.exactNames().contains("typeOnly"));
        assertEquals("score description", rules.exactEntry("score").description());
        assertEquals("NIM", rules.exactEntry("score").context());
        assertEquals("FA", rules.partialEntry("segment").context());
        assertEquals("チェック種別の説明", rules.exactEntry("checkKindLike").description());
        assertEquals("type=string", rules.exactEntry("typeOnly").description());

        Path nimtCatalog = Files.createTempFile("nimt-allowed-variables-", ".txt");
        Files.writeString(nimtCatalog, "fromNimt|string");
        TinyExpressionVariableCatalog.Rules nimtRules =
                TinyExpressionVariableCatalog.loadFromPathList(nimtCatalog.toString(), "test");
        assertEquals("NIM", nimtRules.exactEntry("fromNimt").context());
    }

    @Test
    void analyzerEmitsTe024OnlyForMissingSuffixOutsideQuotesAndComments() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-catalog", ".txt");
        Files.writeString(catalog, String.join("\n",
                "kind_*|string|catalog|partial key",
                "age|number|catalog|exact"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "$kind + $kind_login + $ag + get($age).orElse(1) + '$kind' // $kind\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        long te024Count = errors.stream().filter(error -> error.message().startsWith("[TE024]")).count();
        long te022Count = errors.stream().filter(error -> error.message().startsWith("[TE022]")).count();
        long te021Count = errors.stream().filter(error -> error.message().startsWith("[TE021]")).count();
        assertEquals(1, te024Count);
        assertEquals(1, te022Count);
        assertEquals(0, te021Count);
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .anyMatch(error -> error.message().contains("候補: $age")));
    }

    @Test
    void analyzerEmitsTe021ForUnknownMethodWithSuggestion() throws Exception {
        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer();
        String content = "cosh(1);\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors =
                analyzer.analyze(content, parseResult).errors();
        long te021Count = errors.stream()
                .filter(error -> error.message().startsWith("[TE021]"))
                .count();
        assertEquals(1, te021Count);
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE021]"))
                .anyMatch(error -> error.message().contains("候補: cos")));
    }

    @Test
    void analyzerPrefersTe022SuggestionMatchingDocumentContext() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorea|nim score|nimt",
                "exact|scoreb|fa score|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .anyMatch(error -> error.message().contains("候補: $scoreb")),
                "TE022 suggestion should prefer FA-context candidate when FA hint exists in document");
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .anyMatch(error -> error.message().contains("候補: $scoreb ヒント: context=FA / fa score")),
                "TE022 hint should show context first, then description");
    }

    @Test
    void analyzerIgnoresQuotedOrCommentedContextHintsForTe022Suggestion() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-mask-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:NIM\n'FA'\n// FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore FA tokens in comments/strings and keep NIM context preference");
    }

    @Test
    void analyzerUsesExplicitContextHintBeforeFreeTextContextTokens() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-explicit-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:NIM\n$fa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should prioritize explicit tags:NIM over free-text '$fa' token");
    }

    @Test
    void analyzerAcceptsQuotedExplicitContextHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-quoted-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "context = \"FA\"\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept quoted explicit context and prefer FA candidate");
    }

    @Test
    void analyzerAcceptsListStyleExplicitContextHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-list-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags = core,FA,ops\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept list-style explicit context and prefer FA candidate");
    }

    @Test
    void analyzerAcceptsJsonStyleExplicitContextHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-json-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "\"context\": \"FA\",\n$nimt\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept JSON-style context key and keep FA preference");
    }

    @Test
    void analyzerIgnoresJsonStyleExplicitContextHintInsideComment() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-json-comment-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:NIM\n// \"context\": \"FA\"\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore JSON-style explicit context when it appears only inside comments");
    }

    @Test
    void analyzerIgnoresVariableLikeContextTokenInExplicitContextValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-variable-token-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:NIM\ncontext=$fa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore '$fa' token in explicit context value");
    }

    @Test
    void analyzerIgnoresInlineCommentTokenInExplicitContextValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-inline-comment-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags: NIM // FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore inline-comment context tokens in explicit context value");
    }

    @Test
    void analyzerAcceptsCaseInsensitiveServiceContextHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-service-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "Service = \"fA\"\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept case-insensitive service context hint and keep FA preference");
    }

    @Test
    void analyzerAppliesTenantContextHintOverFreeTextToken() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-tenant-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tenant:NIM\n$fa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should prioritize explicit tenant context over free-text '$fa' token");
    }

    @Test
    void analyzerAcceptsSingleQuotedTenantContextHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-tenant-quoted-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tenant='NIM'\n$fa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should accept single-quoted tenant context hint");
    }

    @Test
    void analyzerAcceptsSingleQuotedJsonLikeContextKey() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-single-quoted-key-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "'context': 'FA'\n$nimt\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept single-quoted JSON-like context key and prefer FA candidate");
    }

    @Test
    void analyzerAcceptsCaseInsensitiveQuotedServiceKey() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-quoted-service-key-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "\"Service\" : \"fA\"\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should accept case-insensitive quoted service key and prefer FA candidate");
    }

    @Test
    void analyzerUsesFirstExplicitContextWhenMultipleHintsExist() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-multi-explicit-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags:NIM\ncontext:FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should use the first explicit context hint when multiple explicit hints exist");
    }

    @Test
    void analyzerUsesFirstRecognizedContextTokenInSingleExplicitValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-single-value-order-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags=core,NIM,FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should use first recognized context token in a single explicit value");
    }

    @Test
    void analyzerRespectsRecognizedTokenOrderInSingleExplicitValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-single-value-order-reverse-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags=core,FA,NIM\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should respect recognized token order in a single explicit value");
    }

    @Test
    void analyzerFallsBackToFreeTextWhenExplicitValueHasNoRecognizedToken() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-fallback-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags=core,ops\nfa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should fall back to free-text context when explicit value has no recognized token");
    }

    @Test
    void analyzerFallsBackToFreeTextWhenExplicitHintExistsOnlyInComment() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-comment-fallback-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "// tags: NIM\nfa\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should ignore comment-only explicit hints and fall back to free-text context");
    }

    @Test
    void analyzerIgnoresBlockCommentTokenInsideExplicitContextValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-block-comment-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags=core /* FA */\nnim\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore block-comment tokens in explicit context value");
    }

    @Test
    void analyzerIgnoresUnterminatedBlockCommentTailInsideExplicitContextValue() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-unterminated-block-comment-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "$scorex\ntags=NIM /* FA\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should ignore unterminated block-comment tail after recognized explicit context token");
    }

    @Test
    void analyzerSkipsExplicitLinesWithoutRecognizedTokenAndUsesNextExplicitHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-next-explicit-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "tags=core,ops\ncontext=FA\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $score")),
                "TE022 should skip explicit lines without recognized token and use the next explicit hint");
    }

    @Test
    void analyzerSkipsCommentOnlyExplicitLineAndUsesNextExplicitHint() throws Exception {
        Path catalog = Files.createTempFile("tinyexpression-context-next-after-comment-catalog", ".tecatalog");
        Files.writeString(catalog, String.join("\n",
                "tinyexpression-catalog-v1",
                "exact|scorexy|nim candidate|nimt",
                "exact|score|fa candidate|fa"));
        TinyExpressionVariableCatalog.Rules rules =
                TinyExpressionVariableCatalog.loadFromPathList(catalog.toString(), "test");

        CalculatorAstAnalyzer analyzer = new CalculatorAstAnalyzer(rules);
        String content = "// tags=FA\ncontext=NIM\n$scorex\n";
        CalculatorLanguageServer.ParseResult parseResult = new CalculatorLanguageServer.ParseResult(
                true,
                content.length(),
                content.length(),
                Parsed.FAILED,
                null);

        List<CalculatorAstAnalyzer.AstError> errors = analyzer.analyze(content, parseResult).errors();
        assertTrue(errors.stream()
                .filter(error -> error.message().startsWith("[TE022]"))
                .filter(error -> error.message().contains("$scorex"))
                .anyMatch(error -> error.message().contains("候補: $scorexy")),
                "TE022 should skip comment-only explicit lines and use next explicit hint");
    }
}
