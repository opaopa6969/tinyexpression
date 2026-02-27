package org.unlaxer.calculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.unlaxer.Token;

/**
 * Lightweight analyzer used by the LSP layer.
 *
 * <p>For TinyExpression migration we keep this minimal and only provide
 * structural checks that are parser-version agnostic.</p>
 */
public final class CalculatorAstAnalyzer {

    private static final Pattern IMPORT_ALIAS_PATTERN =
            Pattern.compile("\\bimport\\s+[^;\\r\\n]*?\\bas\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
    private static final Pattern METHOD_DECLARATION_PATTERN =
            Pattern.compile("\\b(?:byte|short|int|long|float|double|number|Number|string|String|boolean|Boolean|object|Object)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern INVOCATION_PATTERN =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern VARIABLE_REFERENCE_PATTERN =
            Pattern.compile("\\$([\\p{L}_][\\p{L}\\p{N}_]*)");
    private static final Pattern VARIABLE_DECLARATION_PATTERN =
            Pattern.compile("\\b(?:var|variable)\\s+\\$([\\p{L}_][\\p{L}\\p{N}_]*)");
    private static final Pattern TYPED_VARIABLE_PATTERN =
            Pattern.compile("\\$([\\p{L}_][\\p{L}\\p{N}_]*)\\s+as\\b");
    private static final Set<String> PARSER_DEFINED_METHODS =
            TinyExpressionParserMethodCatalog.methodNames();
    private static final Set<String> RESERVED_HEADS = Set.of(
            "if", "match", "get", "var", "variable", "external", "internal", "call",
            "default", "import", "as", "set", "description", "returning",
            "Number", "String", "Boolean", "Float", "Object",
            "number", "string", "boolean", "float", "object");
    private final TinyExpressionVariableCatalog.Rules variableCatalogRules;

    public CalculatorAstAnalyzer() {
        this(TinyExpressionVariableCatalog.loadFromRuntimeConfiguration());
    }

    CalculatorAstAnalyzer(TinyExpressionVariableCatalog.Rules variableCatalogRules) {
        this.variableCatalogRules = variableCatalogRules == null
                ? TinyExpressionVariableCatalog.Rules.empty()
                : variableCatalogRules;
    }

    public AnalysisResult analyze(String content, CalculatorLanguageServer.ParseResult parseResult) {
        List<AstError> errors = new ArrayList<>();
        errors.addAll(findParenthesisErrors(content));
        errors.addAll(findUnknownMethodErrors(content));
        boolean[] ignoredMask = buildIgnoredMask(content);
        List<VariableReference> variableReferences = collectVariableReferences(content, ignoredMask);
        errors.addAll(findPartialKeyVariableErrors(content, variableReferences));
        errors.addAll(findUndefinedVariableErrors(content, ignoredMask, variableReferences));
        return new AnalysisResult(errors, null, null);
    }

    private List<AstError> findParenthesisErrors(String content) {
        List<AstError> errors = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '(') {
                stack.push(index);
            } else if (current == ')') {
                if (stack.isEmpty()) {
                    errors.add(new AstError(toRange(content, index, index + 1),
                            "閉じ括弧に対応する開き括弧がありません"));
                } else {
                    stack.pop();
                }
            }
        }

        while (false == stack.isEmpty()) {
            int index = stack.pop();
            errors.add(new AstError(toRange(content, index, index + 1),
                    "開き括弧が閉じられていません"));
        }

        return errors;
    }

    private List<AstError> findUnknownMethodErrors(String content) {
        Set<String> allowedMethods = new HashSet<>(PARSER_DEFINED_METHODS);
        allowedMethods.addAll(extractImportAliases(content));
        allowedMethods.addAll(extractDeclaredMethods(content));

        List<AstError> errors = new ArrayList<>();
        Set<Integer> reportedOffsets = new HashSet<>();
        Matcher matcher = INVOCATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            int start = matcher.start(1);
            if (reportedOffsets.contains(start)) {
                continue;
            }
            if (allowedMethods.contains(methodName) || RESERVED_HEADS.contains(methodName)) {
                continue;
            }
            if (isInvocationDeclarationContext(content, start)) {
                continue;
            }
            if (hasDollarPrefix(content, start)) {
                continue;
            }
            reportedOffsets.add(start);
            Range range = toRange(content, start, matcher.end(1));
            String suggestion = closestMethodName(methodName, allowedMethods);
            String fix = suggestion == null
                    ? "候補メソッド名へ修正し、必要なら import ... as ...; を追加"
                    : "候補: " + suggestion;
            errors.add(new AstError(
                    range,
                    "[TE021] 未定義メソッド呼び出し: " + methodName
                            + " 修正: " + fix));
        }
        return errors;
    }

    private List<AstError> findPartialKeyVariableErrors(
            String content,
            List<VariableReference> variableReferences) {
        if (variableCatalogRules.partialPrefixes().isEmpty()) {
            return List.of();
        }
        List<AstError> errors = new ArrayList<>();
        Set<Integer> reportedOffsets = new HashSet<>();
        for (VariableReference reference : variableReferences) {
            int start = reference.startOffset();
            String variableName = reference.name();
            if (variableCatalogRules.isMissingPartialSuffix(variableName) == false) {
                continue;
            }
            if (reportedOffsets.add(start) == false) {
                continue;
            }
            String suggestion = "$" + variableName + "_<suffix>";
            errors.add(new AstError(
                    toRange(content, start, reference.endOffset()),
                    "[TE024] partialKey 変数の形式が不正です: $" + variableName
                            + " 修正: " + suggestion + " 形式に修正"));
        }
        return errors;
    }

    private List<AstError> findUndefinedVariableErrors(
            String content,
            boolean[] ignoredMask,
            List<VariableReference> variableReferences) {
        if (variableCatalogRules.isEmpty()) {
            return List.of();
        }
        Set<String> declaredVariables = extractDeclaredVariables(content, ignoredMask);
        List<AstError> errors = new ArrayList<>();
        Set<Integer> reportedOffsets = new HashSet<>();
        for (VariableReference reference : variableReferences) {
            String variableName = reference.name();
            if (variableCatalogRules.isMissingPartialSuffix(variableName)) {
                continue;
            }
            if (declaredVariables.contains(variableName)) {
                continue;
            }
            if (variableCatalogRules.isAllowed(variableName)) {
                continue;
            }
            if (reportedOffsets.add(reference.startOffset()) == false) {
                continue;
            }
            String suggestion = closestVariableName(variableName, declaredVariables);
            String fix = suggestion == null
                    ? "候補変数名へ修正"
                    : "候補: $" + suggestion;
            errors.add(new AstError(
                    toRange(content, reference.startOffset(), reference.endOffset()),
                    "[TE022] 利用可能な変数名ではありません: $" + variableName
                            + " 修正: " + fix));
        }
        return errors;
    }

    private Set<String> extractDeclaredVariables(String content, boolean[] ignoredMask) {
        Set<String> declared = new HashSet<>();
        Matcher declarationMatcher = VARIABLE_DECLARATION_PATTERN.matcher(content);
        while (declarationMatcher.find()) {
            int start = declarationMatcher.start(1) - 1;
            if (isIgnoredOffset(ignoredMask, start)) {
                continue;
            }
            declared.add(declarationMatcher.group(1));
        }
        Matcher typedMatcher = TYPED_VARIABLE_PATTERN.matcher(content);
        while (typedMatcher.find()) {
            int start = typedMatcher.start(1) - 1;
            if (isIgnoredOffset(ignoredMask, start)) {
                continue;
            }
            declared.add(typedMatcher.group(1));
        }
        return declared;
    }

    private List<VariableReference> collectVariableReferences(String content, boolean[] ignoredMask) {
        List<VariableReference> references = new ArrayList<>();
        Matcher matcher = VARIABLE_REFERENCE_PATTERN.matcher(content);
        while (matcher.find()) {
            int start = matcher.start();
            if (isIgnoredOffset(ignoredMask, start)) {
                continue;
            }
            references.add(new VariableReference(matcher.group(1), start, matcher.end()));
        }
        return references;
    }

    private boolean isIgnoredOffset(boolean[] ignoredMask, int offset) {
        if (offset < 0 || offset >= ignoredMask.length) {
            return false;
        }
        return ignoredMask[offset];
    }

    private boolean[] buildIgnoredMask(String content) {
        boolean[] ignoredMask = new boolean[content.length()];
        boolean inSingleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);

            if (inLineComment) {
                ignoredMask[i] = true;
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                ignoredMask[i] = true;
                if (current == '*' && i + 1 < content.length() && content.charAt(i + 1) == '/') {
                    ignoredMask[i + 1] = true;
                    i++;
                    inBlockComment = false;
                }
                continue;
            }

            if (inSingleQuote) {
                ignoredMask[i] = true;
                if (current == '\\' && i + 1 < content.length()) {
                    ignoredMask[i + 1] = true;
                    i++;
                    continue;
                }
                if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }

            if (current == '/' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if (next == '/') {
                    ignoredMask[i] = true;
                    ignoredMask[i + 1] = true;
                    i++;
                    inLineComment = true;
                    continue;
                }
                if (next == '*') {
                    ignoredMask[i] = true;
                    ignoredMask[i + 1] = true;
                    i++;
                    inBlockComment = true;
                    continue;
                }
            }

            if (current == '\'') {
                ignoredMask[i] = true;
                inSingleQuote = true;
            }
        }
        return ignoredMask;
    }

    private String closestMethodName(String unknownMethod, Set<String> allowedMethods) {
        return closestCandidate(unknownMethod, allowedMethods);
    }

    private String closestVariableName(String unknownVariable, Set<String> declaredVariables) {
        Set<String> candidates = new HashSet<>();
        candidates.addAll(variableCatalogRules.exactNames());
        candidates.addAll(declaredVariables);
        for (String prefix : variableCatalogRules.partialPrefixes()) {
            candidates.add(prefix + "_<suffix>");
        }
        return closestCandidate(unknownVariable, candidates);
    }

    private String closestCandidate(String unknown, Set<String> candidates) {
        if (unknown == null || unknown.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String unknownLower = unknown.toLowerCase();
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int distance = levenshtein(unknownLower, candidate.toLowerCase());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            } else if (distance == bestDistance && best != null
                    && candidate.length() < best.length()) {
                best = candidate;
            }
        }
        if (best == null) {
            return null;
        }
        int threshold = Math.max(2, unknown.length() / 2);
        if (bestDistance > threshold) {
            return null;
        }
        return best;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            char leftChar = left.charAt(i - 1);
            for (int j = 1; j <= right.length(); j++) {
                int cost = leftChar == right.charAt(j - 1) ? 0 : 1;
                int insertion = current[j - 1] + 1;
                int deletion = previous[j] + 1;
                int substitution = previous[j - 1] + cost;
                current[j] = Math.min(Math.min(insertion, deletion), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private record VariableReference(String name, int startOffset, int endOffset) {}

    private Set<String> extractImportAliases(String content) {
        Set<String> aliases = new HashSet<>();
        Matcher matcher = IMPORT_ALIAS_PATTERN.matcher(content);
        while (matcher.find()) {
            aliases.add(matcher.group(1));
        }
        return aliases;
    }

    private Set<String> extractDeclaredMethods(String content) {
        Set<String> methods = new HashSet<>();
        Matcher matcher = METHOD_DECLARATION_PATTERN.matcher(content);
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods;
    }

    private boolean isInvocationDeclarationContext(String content, int invocationStart) {
        int previous = skipWhitespaceBackward(content, invocationStart - 1);
        if (previous < 0) {
            return false;
        }
        int wordEnd = previous + 1;
        while (previous >= 0 && Character.isJavaIdentifierPart(content.charAt(previous))) {
            previous--;
        }
        int wordStart = previous + 1;
        if (wordStart >= wordEnd) {
            return false;
        }
        String head = content.substring(wordStart, wordEnd);
        return RESERVED_HEADS.contains(head);
    }

    private boolean hasDollarPrefix(String content, int invocationStart) {
        int previous = skipWhitespaceBackward(content, invocationStart - 1);
        return previous >= 0 && content.charAt(previous) == '$';
    }

    private int skipWhitespaceBackward(String content, int index) {
        int i = Math.min(index, content.length() - 1);
        while (i >= 0 && Character.isWhitespace(content.charAt(i))) {
            i--;
        }
        return i;
    }

    private Range toRange(String content, int startOffset, int endOffset) {
        Position start = offsetToPosition(content, startOffset);
        Position end = offsetToPosition(content, endOffset);
        return new Range(start, end);
    }

    private Position offsetToPosition(String content, int offset) {
        int line = 0;
        int column = 0;
        for (int index = 0; index < offset && index < content.length(); index++) {
            if (content.charAt(index) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new Position(line, column);
    }

    public record AstError(Range range, String message) {}

    public record AnalysisResult(List<AstError> errors, Token astRoot, Double value) {
        public boolean hasValue() {
            return value != null;
        }
    }
}
