package org.unlaxer.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

class TinyExpressionSuggestableParserTest {

    @Test
    void varSnippetUsesSingleDollarAndIncludesDescriptionTemplate() {
        TinyExpressionSuggestableParser parser = new TinyExpressionSuggestableParser();
        List<SuggestableParser.Suggestion> suggestions = parser.suggest("var", new Position(0, 3));

        SuggestableParser.Suggestion varSuggestion = suggestions.stream()
                .filter(s -> "var".equals(s.label()))
                .findFirst()
                .orElseThrow();

        assertEquals("var \\$name as string set if not exists 'value' description='';",
                varSuggestion.insertText());
        assertTrue(varSuggestion.insertText().contains("description=''"));
    }
}
