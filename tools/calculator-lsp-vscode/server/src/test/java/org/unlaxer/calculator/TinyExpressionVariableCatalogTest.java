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
}
