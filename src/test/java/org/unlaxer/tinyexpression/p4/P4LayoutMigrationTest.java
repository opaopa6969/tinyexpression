package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.unlaxer.dsl.bootstrap.UBNFModuleLoader;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.TinyExpressionP4Parser;
import org.unlaxer.tinyexpression.p4.ubnfc.generated.api.ParseOptions;

/** Real P4 scopes and retained text compared with the old policy and production ubnfc. */
public class P4LayoutMigrationTest extends P4LayoutTestSupport {
    @Test public void authoringSnapshotShowsTheExactDefinitionAndPinnedOrigin() throws Exception {
        var snapshot = org.unlaxer.dsl.tooling.VocabularyOrigins.inspect(Path.of(
            "tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf"));
        var layout = snapshot.getAsJsonArray("modules").asList().stream()
            .map(value -> value.getAsJsonObject()).filter(value -> value.get("alias").getAsString().equals("layout"))
            .findFirst().orElseThrow();
        var identity = layout.getAsJsonObject("identity");
        assertEquals("std/layout", identity.get("id").getAsString());
        assertEquals("1.0.0", identity.get("version").getAsString());
        assertEquals("2701f6884ea6c712d28c76ec3d340c3b27fca4575846239bd0854933205f4b67",
            identity.get("sha256").getAsString());
        assertEquals("layout.ubnf", identity.get("file").getAsString());
        var definition = layout.getAsJsonArray("definitions").asList().stream()
            .map(value -> value.getAsJsonObject())
            .filter(value -> value.get("name").getAsString().equals("SPACES_AND_COMMENTS"))
            .findFirst().orElseThrow();
        String text = layout.get("text").getAsString();
        assertEquals("token SPACES_AND_COMMENTS ::= SPACES | LINE_COMMENT | BLOCK_COMMENT;",
            text.substring(text.offsetByCodePoints(0, definition.get("start").getAsInt()),
                           text.offsetByCodePoints(0, definition.get("end").getAsInt())));
        for (var value : snapshot.getAsJsonArray("whitespace"))
            assertEquals("layout.SPACES_AND_COMMENTS", value.getAsJsonObject().get("policy").getAsString());
    }

    @Test public void realP4KeepsPolicyScopesCursorsAstAndOwnedSourcePositions() throws Exception {
        Path old = temporary.newFolder("old-layout").toPath().resolve("tinyexpression-p4.ubnf");
        Path source = Path.of("tools/tinyexpression-p4-lsp-vscode/grammar/tinyexpression-p4.ubnf");
        Process adapter = new ProcessBuilder("python3", "scripts/prepare-ubnfc-layout.py",
            source.toString(), old.toString()).inheritIO().start();
        assertEquals(0, adapter.waitFor());
        Files.writeString(old, Files.readString(old).replace(
            "@package: org.unlaxer.tinyexpression.generated.p4", "@package: org.example.ruletrivia"));
        var grammar = UBNFModuleLoader.load(old).grammars().get(0);
        var rows = JsonParser.parseString(Files.readString(Path.of(
            "src/test/resources/p4-layout/actual-p4.json"))).getAsJsonArray();
        try (var loader = compileJava(grammar)) {
            Class<?> oldParsers = loader.loadClass("org.example.ruletrivia.TinyExpressionP4Parsers");
            Parser oldRoot = (Parser) oldParsers.getMethod("getRootParser").invoke(null);
            Class<?> oldMapper = loader.loadClass("org.example.ruletrivia.TinyExpressionP4Mapper");
            for (var element : rows) {
                var row = element.getAsJsonObject();
                String id = row.get("id").getAsString(), input = row.get("input").getAsString();
                var oracle = row.get("prefix");
                assertEquals(id + " old independent cursor oracle", oracle, prefix(oldRoot, input));
                assertEquals(id + " new independent cursor oracle", oracle,
                    prefix(TinyExpressionP4Parsers.getRootParser(), input));
                var production = TinyExpressionP4Parser.parseEntry("TinyExpressionP4", "Formula", input,
                    new ParseOptions(true, false, false, true, Map.of()));
                assertEquals(id + " production ubnfc acceptance", row.get("ok").getAsBoolean(), production.ok());
                assertEquals(id + " production consumed", oracle.getAsJsonArray().get(1).getAsInt(), production.consumedCp());
                assertEquals(id + " production matched", oracle.getAsJsonArray().get(2).getAsInt(), production.matchedCp());
                if (row.get("ok").getAsBoolean()) {
                    Object oldMapped = oldMapper.getMethod("parseWithSourceMap", String.class).invoke(null, input);
                    Object oldAst = oldMapped.getClass().getMethod("ast").invoke(oldMapped);
                    var mapped = TinyExpressionP4Mapper.parseWithSourceMap(input);
                    assertEquals(id + " every AST field and node span", canonical(oldAst, oldMapped),
                        canonical(mapped.ast(), mapped));
                    var owned = P4PreferredAstMapper.parseByAstSimpleNamesDetailed(input,
                        java.util.List.of("FormulaExpr"), 0L);
                    assertEquals(id + " source-preserving compatibility mask",
                        org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(input),
                        owned.sourceText().text(owned.ast()));
                    assertArrayEquals(id + " source-preserving position", new int[]{0, input.codePointCount(0, input.length())},
                        owned.sourceText().spanOf(owned.ast()).orElseThrow());
                }
            }
        }
    }
}
