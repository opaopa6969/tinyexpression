package org.unlaxer.tinyexpression.lsp.p4;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight UBNF literal extraction used by tooling conformance tests. */
final class UbnfVocabulary {
    private static final Pattern QUOTED_LITERAL =
        Pattern.compile("'((?:\\\\.|[^'\\\\])*)'");
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    private UbnfVocabulary() {}

    static Set<String> wordLiterals(String ubnf) {
        Set<String> words = new LinkedHashSet<>();
        boolean inRule = false;

        for (String rawLine : ubnf.split("\\R", -1)) {
            String line = stripLineComment(rawLine);
            if (!inRule && line.contains("::=")) {
                inRule = true;
            }
            if (!inRule) {
                continue;
            }

            Matcher matcher = QUOTED_LITERAL.matcher(line);
            while (matcher.find()) {
                String literal = matcher.group(1).replace("\\'", "'");
                if (WORD.matcher(literal).matches()) {
                    words.add(literal);
                }
            }

            if (hasUnquotedSemicolon(line)) {
                inRule = false;
            }
        }
        return words;
    }

    static Set<String> missingCompletions(
            String ubnf, Set<String> completions, Set<String> intentionallyOmitted) {
        Set<String> missing = wordLiterals(ubnf);
        missing.removeAll(completions);
        missing.removeAll(intentionallyOmitted);
        return missing;
    }

    private static String stripLineComment(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i + 1 < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && quoted) {
                escaped = true;
                continue;
            }
            if (current == '\'') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && current == '/' && line.charAt(i + 1) == '/') {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static boolean hasUnquotedSemicolon(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\' && quoted) {
                escaped = true;
                continue;
            }
            if (current == '\'') {
                quoted = !quoted;
            } else if (!quoted && current == ';') {
                return true;
            }
        }
        return false;
    }
}
