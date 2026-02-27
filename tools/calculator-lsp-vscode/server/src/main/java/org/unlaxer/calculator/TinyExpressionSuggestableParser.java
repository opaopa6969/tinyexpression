package org.unlaxer.calculator;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;

/**
 * Suggestion provider for TinyExpression DSL.
 */
public final class TinyExpressionSuggestableParser implements SuggestableParser {

    private static final List<Suggestion> SUGGESTIONS = List.of(
            keywordSnippet("if", "if expression", "if($1){$2}else{$3}"),
            keywordSnippet("match", "match expression", "match{$1 -> $2, default -> $3}"),
            keywordSnippet("var", "variable declaration", "var \\$name as string set if not exists 'value' description='';"),
            keywordSnippet("external", "external function call", "external returning as ${1:boolean} ${2:func}($3)"),
            keywordSnippet("import", "import external function", "import ${1:Class}#${2:method} as ${3:alias};"),
            keywordSnippet("default", "default case", "default -> $1"),
            functionSnippet("sin", "sine function", "sin($1)"),
            functionSnippet("cos", "cosine function", "cos($1)"),
            functionSnippet("tan", "tangent function", "tan($1)"),
            functionSnippet("sqrt", "square root function", "sqrt($1)"),
            functionSnippet("min", "minimum function", "min($1, $2)"),
            functionSnippet("max", "maximum function", "max($1, $2)")
    );

    @Override
    public List<String> getTriggerCharacters() {
        return List.of("i", "m", "v", "e", "$");
    }

    @Override
    public List<Suggestion> suggest(String content, Position position) {
        int offset = positionToOffset(content, position);
        if (offset < 0 || offset > content.length()) {
            return List.of();
        }

        int wordStart = findWordStart(content, offset);
        String currentWord = content.substring(wordStart, offset).toLowerCase();

        List<Suggestion> matches = new ArrayList<>();
        for (Suggestion suggestion : SUGGESTIONS) {
            if (currentWord.isEmpty() || suggestion.label().toLowerCase().startsWith(currentWord)) {
                matches.add(suggestion);
            }
        }
        return matches;
    }

    private static Suggestion functionSnippet(String label, String detail, String snippet) {
        return new Suggestion(label, detail, CompletionItemKind.Function, snippet, InsertTextFormat.Snippet);
    }

    private static Suggestion keywordSnippet(String label, String detail, String snippet) {
        return new Suggestion(label, detail, CompletionItemKind.Keyword, snippet, InsertTextFormat.Snippet);
    }

    private static int findWordStart(String content, int offset) {
        int wordStart = offset;
        while (wordStart > 0) {
            char previous = content.charAt(wordStart - 1);
            if (false == (Character.isLetterOrDigit(previous) || previous == '_' || previous == '$')) {
                break;
            }
            wordStart--;
        }
        return wordStart;
    }

    private static int positionToOffset(String content, Position position) {
        int offset = 0;
        int line = 0;
        int column = 0;

        int targetLine = position.getLine();
        int targetColumn = position.getCharacter();

        while (offset < content.length()) {
            if (line == targetLine && column == targetColumn) {
                return offset;
            }

            char current = content.charAt(offset);
            if (current == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
            offset++;
        }

        if (line == targetLine && column == targetColumn) {
            return offset;
        }

        return -1;
    }
}
