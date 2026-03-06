package org.unlaxer.calculator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final Pattern CONTEXT_HINT_PATTERN =
            Pattern.compile("\\b(nimt?|fa)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_CONTEXT_LINE_PATTERN =
            Pattern.compile("(?im)^\\s*[\"']?(tags?|context|product|service|tenant)[\"']?\\s*[:=]\\s*(.+)$");
    private static final Pattern NUMBER_LITERAL_PATTERN =
            Pattern.compile("^[+-]?\\d+(?:\\.\\d+)?$");
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
        boolean[] ignoredMask = buildIgnoredMask(content);
        errors.addAll(findAdvancedIfConditionErrors(content, ignoredMask));
        errors.addAll(findAdvancedMinMaxArityErrors(content, ignoredMask));
        errors.addAll(findUnknownMethodErrors(content, ignoredMask));
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

    private List<AstError> findUnknownMethodErrors(String content, boolean[] ignoredMask) {
        Set<String> allowedMethods = new HashSet<>(PARSER_DEFINED_METHODS);
        allowedMethods.addAll(extractImportAliases(content, ignoredMask));
        allowedMethods.addAll(extractDeclaredMethods(content, ignoredMask));

        List<AstError> errors = new ArrayList<>();
        Set<Integer> reportedOffsets = new HashSet<>();
        Matcher matcher = INVOCATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            int start = matcher.start(1);
            if (isIgnoredOffset(ignoredMask, start)) {
                continue;
            }
            if (reportedOffsets.contains(start)) {
                continue;
            }
            if (allowedMethods.contains(methodName) || RESERVED_HEADS.contains(methodName)) {
                continue;
            }
            if (hasDotQualifier(content, start)) {
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

    private List<AstError> findAdvancedIfConditionErrors(String content, boolean[] ignoredMask) {
        List<AstError> errors = new ArrayList<>();
        int search = 0;
        while (search < content.length()) {
            int ifIndex = content.indexOf("if", search);
            if (ifIndex < 0) {
                break;
            }
            search = ifIndex + 2;
            if (isIgnoredOffset(ignoredMask, ifIndex)
                    || isWordBoundary(content, ifIndex - 1) == false
                    || isWordBoundary(content, ifIndex + 2) == false) {
                continue;
            }
            int open = skipWhitespaceForward(content, ifIndex + 2);
            if (open < 0 || open >= content.length() || content.charAt(open) != '(') {
                continue;
            }
            int close = findMatchingParenthesis(content, ignoredMask, open);
            if (close <= open) {
                continue;
            }
            String condition = content.substring(open + 1, close).trim();
            if (condition.isEmpty() || looksBooleanCondition(condition)) {
                continue;
            }
            int conditionEnd = Math.min(close, open + 1 + condition.length());
            errors.add(new AstError(
                    toRange(content, open + 1, Math.max(open + 1, conditionEnd)),
                    "[TE011] if 条件式がboolean形ではありません: " + condition
                            + " 修正: 比較演算子またはboolean式に修正"));
        }
        return errors;
    }

    private List<AstError> findAdvancedMinMaxArityErrors(String content, boolean[] ignoredMask) {
        List<AstError> errors = new ArrayList<>();
        Set<Integer> reported = new HashSet<>();
        Matcher matcher = INVOCATION_PATTERN.matcher(content);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            if ("min".equals(methodName) == false && "max".equals(methodName) == false) {
                continue;
            }
            int nameStart = matcher.start(1);
            int open = skipWhitespaceForward(content, matcher.end(1));
            if (isIgnoredOffset(ignoredMask, nameStart)
                    || open < 0
                    || open >= content.length()
                    || content.charAt(open) != '('
                    || hasDotQualifier(content, nameStart)
                    || hasDollarPrefix(content, nameStart)
                    || reported.add(nameStart) == false) {
                continue;
            }
            int close = findMatchingParenthesis(content, ignoredMask, open);
            if (close <= open) {
                continue;
            }
            String argumentText = content.substring(open + 1, close);
            if (hasMinMaxArityIssue(argumentText, ignoredMask, open + 1)) {
                errors.add(new AstError(
                        toRange(content, nameStart, matcher.end(1)),
                        "[TE015] min/max の引数数が不正です: " + methodName
                                + " 修正: min/max は2引数で呼び出してください"));
            }
        }
        return errors;
    }

    private boolean hasMinMaxArityIssue(String arguments, boolean[] ignoredMask, int argumentBaseOffset) {
        if (arguments.trim().isEmpty()) {
            return true;
        }
        List<String> segments = splitTopLevelArguments(arguments, ignoredMask, argumentBaseOffset);
        if (segments.size() != 2) {
            return true;
        }
        return segments.get(0).isBlank() || segments.get(1).isBlank();
    }

    private List<String> splitTopLevelArguments(String arguments, boolean[] ignoredMask, int baseOffset) {
        List<String> segments = new ArrayList<>();
        int depth = 0;
        int segmentStart = 0;
        for (int i = 0; i < arguments.length(); i++) {
            int absolute = baseOffset + i;
            if (isIgnoredOffset(ignoredMask, absolute)) {
                continue;
            }
            char c = arguments.charAt(i);
            if (c == '(') {
                depth++;
                continue;
            }
            if (c == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (c == ',' && depth == 0) {
                segments.add(arguments.substring(segmentStart, i).trim());
                segmentStart = i + 1;
            }
        }
        segments.add(arguments.substring(segmentStart).trim());
        return segments;
    }

    private int findMatchingParenthesis(String content, boolean[] ignoredMask, int openOffset) {
        int depth = 0;
        for (int i = openOffset; i < content.length(); i++) {
            if (isIgnoredOffset(ignoredMask, i)) {
                continue;
            }
            char c = content.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean looksBooleanCondition(String condition) {
        String trimmed = condition == null ? "" : condition.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String lowered = trimmed.toLowerCase();
        if ("true".equals(lowered) || "false".equals(lowered)) {
            return true;
        }
        if (lowered.contains("==")
                || lowered.contains("!=")
                || lowered.contains(">=")
                || lowered.contains("<=")
                || lowered.contains(">")
                || lowered.contains("<")
                || lowered.contains("&")
                || lowered.contains("|")
                || lowered.contains("!")
                || lowered.contains(" and ")
                || lowered.contains(" or ")) {
            return true;
        }
        if (NUMBER_LITERAL_PATTERN.matcher(trimmed).matches()) {
            return false;
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'") && trimmed.length() >= 2) {
            return false;
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return false;
        }
        // Keep variable or invocation-like conditions as likely-boolean to avoid false positives.
        return true;
    }

    private int skipWhitespaceForward(String content, int index) {
        int i = Math.max(0, index);
        while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
            i++;
        }
        return i;
    }

    private boolean isWordBoundary(String content, int index) {
        if (index < 0 || index >= content.length()) {
            return true;
        }
        return Character.isLetterOrDigit(content.charAt(index)) == false && content.charAt(index) != '_';
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
            VariableSuggestion suggestion = closestVariableName(content, ignoredMask, variableName, declaredVariables);
            String fix = suggestion == null
                    ? "候補変数名へ修正"
                    : "候補: $" + suggestion.name() + formatVariableSuggestionHint(suggestion);
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

    private String formatVariableSuggestionHint(VariableSuggestion suggestion) {
        if (suggestion == null) {
            return "";
        }
        List<String> hints = new ArrayList<>();
        if (suggestion.context() != null && suggestion.context().isBlank() == false) {
            hints.add("context=" + suggestion.context());
        }
        if (suggestion.description() != null && suggestion.description().isBlank() == false) {
            hints.add(suggestion.description());
        }
        if (hints.isEmpty()) {
            return "";
        }
        return " ヒント: " + String.join(" / ", hints);
    }

    private VariableSuggestion closestVariableName(
            String content,
            boolean[] ignoredMask,
            String unknownVariable,
            Set<String> declaredVariables) {
        List<VariableSuggestionCandidate> candidates = new ArrayList<>();
        for (String declared : declaredVariables) {
            candidates.add(new VariableSuggestionCandidate(declared, 0, "", ""));
        }
        for (String exact : variableCatalogRules.exactNames()) {
            TinyExpressionVariableCatalog.CatalogEntryInfo info = variableCatalogRules.exactEntry(exact);
            String description = info == null ? "" : info.description();
            String context = info == null ? "" : info.context();
            candidates.add(new VariableSuggestionCandidate(exact, 1, description, context));
        }
        for (String prefix : variableCatalogRules.partialPrefixes()) {
            TinyExpressionVariableCatalog.CatalogEntryInfo info = variableCatalogRules.partialEntry(prefix);
            String description = info == null ? "" : info.description();
            String context = info == null ? "" : info.context();
            candidates.add(new VariableSuggestionCandidate(prefix + "_<suffix>", 2, description, context));
        }
        if (unknownVariable == null || unknownVariable.isBlank() || candidates.isEmpty()) {
            return null;
        }
        Set<String> preferredContexts = detectPreferredContexts(content, ignoredMask);
        String unknownLower = unknownVariable.toLowerCase();
        List<ScoredVariableCandidate> scored = new ArrayList<>();
        for (VariableSuggestionCandidate candidate : candidates) {
            if (candidate.name() == null || candidate.name().isBlank()) {
                continue;
            }
            int distance = variableDistance(unknownLower, candidate.name().toLowerCase());
            int contextPenalty = variableContextPenalty(candidate.context(), preferredContexts);
            int score = distance * 100 + candidate.priority() * 10 + contextPenalty;
            scored.add(new ScoredVariableCandidate(
                    candidate.name(),
                    candidate.priority(),
                    candidate.description(),
                    candidate.context(),
                    distance,
                    contextPenalty,
                    score));
        }
        if (scored.isEmpty()) {
            return null;
        }
        scored.sort(Comparator
                .comparingInt(ScoredVariableCandidate::score)
                .thenComparingInt(ScoredVariableCandidate::distance)
                .thenComparingInt(ScoredVariableCandidate::priority)
                .thenComparingInt(ScoredVariableCandidate::contextPenalty)
                .thenComparingInt(candidate -> candidate.name().length()));
        ScoredVariableCandidate best = scored.get(0);
        int threshold = Math.max(2, unknownVariable.length() / 2 + 1);
        if (best.distance() > threshold) {
            return null;
        }
        return new VariableSuggestion(best.name(), best.description(), best.context());
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

    private int variableDistance(String unknownLower, String candidateLower) {
        if (candidateLower.endsWith("_<suffix>")) {
            String prefix = candidateLower.substring(0, candidateLower.length() - "_<suffix>".length());
            if (unknownLower.equals(prefix)) {
                return 0;
            }
            if (unknownLower.startsWith(prefix + "_")) {
                return 1;
            }
            return levenshtein(unknownLower, prefix) + 1;
        }
        return levenshtein(unknownLower, candidateLower);
    }

    private Set<String> detectPreferredContexts(String content, boolean[] ignoredMask) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Set<String> explicit = detectExplicitPreferredContexts(content, ignoredMask);
        if (explicit.isEmpty() == false) {
            return explicit;
        }
        Set<String> contexts = new HashSet<>();
        Matcher matcher = CONTEXT_HINT_PATTERN.matcher(content);
        while (matcher.find()) {
            if (ignoredMask != null && isIgnoredOffset(ignoredMask, matcher.start())) {
                continue;
            }
            if (hasVariablePrefix(content, matcher.start())) {
                continue;
            }
            String normalized = normalizeContextForMatching(matcher.group(1));
            if (normalized.isBlank() == false) {
                contexts.add(normalized);
            }
        }
        return contexts;
    }

    private Set<String> detectExplicitPreferredContexts(String content, boolean[] ignoredMask) {
        Matcher matcher = EXPLICIT_CONTEXT_LINE_PATTERN.matcher(content);
        while (matcher.find()) {
            int keyStart = matcher.start(1);
            if (ignoredMask != null && isIgnoredOffset(ignoredMask, keyStart)) {
                continue;
            }
            String value = stripInlineComment(matcher.group(2));
            if (value == null || value.isBlank()) {
                continue;
            }
            Matcher valueMatcher = CONTEXT_HINT_PATTERN.matcher(value);
            while (valueMatcher.find()) {
                if (hasVariablePrefix(value, valueMatcher.start(1))) {
                    continue;
                }
                String normalized = normalizeContextForMatching(valueMatcher.group(1));
                if (normalized.isBlank() == false) {
                    return Set.of(normalized);
                }
            }
        }
        return Set.of();
    }

    private boolean hasVariablePrefix(String content, int offset) {
        return offset > 0 && content.charAt(offset - 1) == '$';
    }

    private String stripInlineComment(String value) {
        if (value == null) {
            return "";
        }
        String withoutLineComment = value;
        int lineCommentStart = withoutLineComment.indexOf("//");
        if (lineCommentStart >= 0) {
            withoutLineComment = withoutLineComment.substring(0, lineCommentStart);
        }
        StringBuilder cleaned = new StringBuilder(withoutLineComment.length());
        int index = 0;
        while (index < withoutLineComment.length()) {
            if (index + 1 < withoutLineComment.length()
                    && withoutLineComment.charAt(index) == '/'
                    && withoutLineComment.charAt(index + 1) == '*') {
                int blockEnd = withoutLineComment.indexOf("*/", index + 2);
                if (blockEnd < 0) {
                    break;
                }
                index = blockEnd + 2;
                continue;
            }
            cleaned.append(withoutLineComment.charAt(index));
            index++;
        }
        return cleaned.toString();
    }

    private int variableContextPenalty(String candidateContext, Set<String> preferredContexts) {
        if (preferredContexts == null || preferredContexts.isEmpty()) {
            return 0;
        }
        String normalized = normalizeContextForMatching(candidateContext);
        if (normalized.isBlank()) {
            return 1;
        }
        return preferredContexts.contains(normalized) ? 0 : 2;
    }

    private String normalizeContextForMatching(String rawContext) {
        if (rawContext == null || rawContext.isBlank()) {
            return "";
        }
        String lowered = rawContext.trim().toLowerCase();
        if (lowered.startsWith("nim")) {
            return "NIM";
        }
        if (lowered.startsWith("fa")) {
            return "FA";
        }
        return rawContext.trim().toUpperCase();
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
    private record VariableSuggestion(String name, String description, String context) {}
    private record VariableSuggestionCandidate(String name, int priority, String description, String context) {}
    private record ScoredVariableCandidate(
            String name,
            int priority,
            String description,
            String context,
            int distance,
            int contextPenalty,
            int score) {}

    private Set<String> extractImportAliases(String content, boolean[] ignoredMask) {
        Set<String> aliases = new HashSet<>();
        Matcher matcher = IMPORT_ALIAS_PATTERN.matcher(content);
        while (matcher.find()) {
            if (isIgnoredOffset(ignoredMask, matcher.start(1))) {
                continue;
            }
            aliases.add(matcher.group(1));
        }
        return aliases;
    }

    private Set<String> extractDeclaredMethods(String content, boolean[] ignoredMask) {
        Set<String> methods = new HashSet<>();
        Matcher matcher = METHOD_DECLARATION_PATTERN.matcher(content);
        while (matcher.find()) {
            if (isIgnoredOffset(ignoredMask, matcher.start(1))) {
                continue;
            }
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

    private boolean hasDotQualifier(String content, int invocationStart) {
        int previous = skipWhitespaceBackward(content, invocationStart - 1);
        return previous >= 0 && content.charAt(previous) == '.';
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
