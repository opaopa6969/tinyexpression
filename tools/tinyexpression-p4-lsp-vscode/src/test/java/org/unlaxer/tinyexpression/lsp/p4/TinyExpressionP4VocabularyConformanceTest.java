package org.unlaxer.tinyexpression.lsp.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class TinyExpressionP4VocabularyConformanceTest {
    /**
     * Compatibility spellings are accepted by the grammar, but completion
     * deliberately teaches the canonical lower-case type spelling.
     */
    private static final Map<String, String> INTENTIONALLY_OMITTED = Map.of(
        "Number", "compatibility alias; suggest canonical number",
        "Float", "compatibility alias; suggest canonical float",
        "String", "compatibility alias; suggest canonical string",
        "Boolean", "compatibility alias; suggest canonical boolean",
        "Object", "compatibility alias; suggest canonical object");

    @Test
    public void everyGrammarWordIsCompletedOrExplicitlyOmitted() throws IOException {
        String grammar = Files.readString(grammarPath());
        Set<String> completions = TinyExpressionP4LanguageServerExt.staticCompletionVocabulary();
        Set<String> missing = UbnfVocabulary.missingCompletions(
            grammar, completions, INTENTIONALLY_OMITTED.keySet());

        assertTrue("UBNF words missing from LSP completion classification: " + missing,
            missing.isEmpty());

        Set<String> grammarWords = UbnfVocabulary.wordLiterals(grammar);
        Set<String> stale = new java.util.LinkedHashSet<>(completions);
        stale.removeAll(grammarWords);
        assertTrue("LSP static completions absent from UBNF: " + stale, stale.isEmpty());
    }

    @Test
    public void syntheticGrammarAdditionIsReportedAsMissing() {
        String fixture = "grammar Fixture { Root ::= 'existing' | 'futureKeyword' ; }";
        Set<String> missing = UbnfVocabulary.missingCompletions(
            fixture, Set.of("existing"), Set.of());

        assertEquals(Set.of("futureKeyword"), missing);
    }

    @Test
    public void intentionalDifferencesAlwaysCarryAReason() {
        Map<String, String> missingReasons = new LinkedHashMap<>();
        INTENTIONALLY_OMITTED.forEach((word, reason) -> {
            if (reason == null || reason.isBlank()) {
                missingReasons.put(word, reason);
            }
        });
        assertTrue("Every allow-list entry needs a reason: " + missingReasons,
            missingReasons.isEmpty());
    }

    private static Path grammarPath() {
        Path modulePath = Path.of(System.getProperty("basedir", "."),
            "grammar", "tinyexpression-p4.ubnf");
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        return Path.of("tools", "tinyexpression-p4-lsp-vscode", "grammar",
            "tinyexpression-p4.ubnf");
    }
}
