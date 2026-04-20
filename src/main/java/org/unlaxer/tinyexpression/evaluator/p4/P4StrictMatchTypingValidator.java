package org.unlaxer.tinyexpression.evaluator.p4;

import java.lang.reflect.RecordComponent;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.BooleanCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.NumberCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST.StringCaseValueExpr;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

/**
 * Semantic strict-typing guard for P4 match expressions.
 * <p>
 * The current UBNF grammar can distinguish match result families
 * (number/string/boolean), but direct {@code $var} and {@code internal foo()}
 * case values are still syntactically ambiguous because {@code VariableRef}
 * and {@code MethodInvocation} participate in multiple expression families.
 * <p>
 * This validator rejects those direct ambiguous shapes from the P4 exact-parse
 * path until declaration-aware type recovery is wired into the generated AST.
 */
public final class P4StrictMatchTypingValidator {

  private static final Pattern BARE_VARIABLE_PATTERN = Pattern.compile(
      "^\\$[\\p{L}_][\\p{L}\\p{N}_]*(?:\\s+as\\s+(number|float|string|boolean|object))?$");

  private static final Pattern BARE_METHOD_INVOCATION_PATTERN = Pattern.compile(
      "^(?:call\\s+internal|call|internal)\\s+[\\p{L}_][\\p{L}\\p{N}_]*\\s*\\(.*\\)$",
      Pattern.DOTALL);

  private P4StrictMatchTypingValidator() {}

  public static Optional<String> firstViolation(TinyExpressionP4AST ast, String formula) {
    return firstViolationDetail(ast, formula).map(Violation::message);
  }

  public static void validateOrThrow(TinyExpressionP4AST ast, String formula) {
    firstViolation(ast, formula).ifPresent(message -> {
      throw new IllegalArgumentException(message);
    });
  }

  public static Optional<String> firstHeuristicViolation(String formula, ExpressionType resultType) {
    return firstHeuristicViolationDetail(formula, resultType).map(Violation::message);
  }

  public static Optional<Violation> firstViolationDetail(TinyExpressionP4AST ast, String formula) {
    if (ast == null || formula == null || formula.isBlank()) {
      return Optional.empty();
    }
    return firstViolationRecursive(ast, formula, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
  }

  public static Optional<Violation> firstHeuristicViolationDetail(String formula, ExpressionType resultType) {
    ExpectedType expectedType = ExpectedType.from(resultType);
    if (expectedType == null || formula == null) {
      return Optional.empty();
    }
    MatchBody matchBody = rootMatchBody(formula);
    if (matchBody == null) {
      return Optional.empty();
    }
    for (Segment caseClause : splitTopLevel(matchBody.body(), ',', matchBody.bodyStartOffset())) {
      Segment rhs = extractCaseValue(caseClause);
      if (rhs == null) {
        continue;
      }
      Optional<Violation> violation = validateDirectCaseValueSnippet(rhs.text(), expectedType, rhs.startOffset(), rhs.endOffset());
      if (violation.isPresent()) {
        return violation;
      }
    }
    return Optional.empty();
  }

  public static Optional<Violation> firstHeuristicViolationAnyExpectedType(String formula) {
    if (formula == null) {
      return Optional.empty();
    }
    MatchBody matchBody = rootMatchBody(formula);
    if (matchBody == null) {
      return Optional.empty();
    }
    for (Segment caseClause : splitTopLevel(matchBody.body(), ',', matchBody.bodyStartOffset())) {
      Segment rhs = extractCaseValue(caseClause);
      if (rhs == null) {
        continue;
      }
      Optional<Violation> violation = validateDirectCaseValueSnippetWithoutExpectedType(rhs.text(), rhs.startOffset(), rhs.endOffset());
      if (violation.isPresent()) {
        return violation;
      }
    }
    return Optional.empty();
  }

  private static Optional<Violation> firstViolationRecursive(Object node, String formula, Set<Object> visited) {
    if (node == null || visited.add(node) == false) {
      return Optional.empty();
    }
    Optional<Violation> directViolation = switch (node) {
      case NumberCaseValueExpr numberCaseValue ->
          validateDirectCaseValue(formula, numberCaseValue, ExpectedType.NUMBER);
      case StringCaseValueExpr stringCaseValue ->
          validateDirectCaseValue(formula, stringCaseValue, ExpectedType.STRING);
      case BooleanCaseValueExpr booleanCaseValue ->
          validateDirectCaseValue(formula, booleanCaseValue, ExpectedType.BOOLEAN);
      default -> Optional.empty();
    };
    if (directViolation.isPresent()) {
      return directViolation;
    }

    if (node instanceof List<?> list) {
      for (Object child : list) {
        Optional<Violation> violation = firstViolationRecursive(child, formula, visited);
        if (violation.isPresent()) {
          return violation;
        }
      }
      return Optional.empty();
    }

    Class<?> nodeClass = node.getClass();
    if (!nodeClass.isRecord()) {
      return Optional.empty();
    }
    for (RecordComponent component : nodeClass.getRecordComponents()) {
      try {
        Object child = component.getAccessor().invoke(node);
        Optional<Violation> violation = firstViolationRecursive(child, formula, visited);
        if (violation.isPresent()) {
          return violation;
        }
      } catch (ReflectiveOperationException ignored) {
        // Skip inaccessible synthetic components and continue validating others.
      }
    }
    return Optional.empty();
  }

  private static Optional<Violation> validateDirectCaseValue(
      String formula, Object caseValueNode, ExpectedType expectedType) {

    Optional<int[]> span = TinyExpressionP4Mapper.sourceSpanOf(caseValueNode);
    if (span.isEmpty()) {
      return Optional.empty();
    }
    int start = Math.max(0, Math.min(span.get()[0], formula.length()));
    int end = Math.max(start, Math.min(span.get()[1], formula.length()));
    String snippet = formula.substring(start, end);
    return validateDirectCaseValueSnippet(snippet, expectedType, start, end);
  }

  private static Optional<Violation> validateDirectCaseValueSnippet(
      String snippet, ExpectedType expectedType, int startOffset, int endOffset) {
    String normalized = normalizeCaseValueSnippet(snippet);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }

    Matcher variableMatcher = BARE_VARIABLE_PATTERN.matcher(normalized);
    if (variableMatcher.matches()) {
      String actualHint = variableMatcher.group(1);
      if (!expectedType.accepts(actualHint)) {
        return Optional.of(new Violation(
            "P4 strict match typing rejected direct "
                + expectedType.label + " case value: " + normalized,
            startOffset,
            endOffset,
            ViolationKind.DIRECT_VARIABLE_CASE_VALUE,
            normalized));
      }
      return Optional.empty();
    }

    if (BARE_METHOD_INVOCATION_PATTERN.matcher(normalized).matches()) {
      return Optional.of(new Violation(
          "P4 strict match typing rejected direct method invocation in "
              + expectedType.label + " match case: " + normalized,
          startOffset,
          endOffset,
          ViolationKind.DIRECT_METHOD_INVOCATION,
          normalized));
    }
    return Optional.empty();
  }

  private static Optional<Violation> validateDirectCaseValueSnippetWithoutExpectedType(
      String snippet, int startOffset, int endOffset) {
    String normalized = normalizeCaseValueSnippet(snippet);
    if (normalized.isEmpty()) {
      return Optional.empty();
    }
    Matcher variableMatcher = BARE_VARIABLE_PATTERN.matcher(normalized);
    if (variableMatcher.matches() && variableMatcher.group(1) == null) {
      return Optional.of(new Violation(
          "P4 strict match typing rejected direct match case variable without inline type hint: "
              + normalized,
          startOffset,
          endOffset,
          ViolationKind.DIRECT_VARIABLE_CASE_VALUE,
          normalized));
    }
    if (BARE_METHOD_INVOCATION_PATTERN.matcher(normalized).matches()) {
      return Optional.of(new Violation(
          "P4 strict match typing rejected direct method invocation in match case: " + normalized,
          startOffset,
          endOffset,
          ViolationKind.DIRECT_METHOD_INVOCATION,
          normalized));
    }
    return Optional.empty();
  }

  private static String normalizeCaseValueSnippet(String snippet) {
    if (snippet == null) {
      return "";
    }
    String normalized = snippet
        .replaceAll("(?s)/\\*.*?\\*/", " ")
        .replaceAll("(?m)//.*$", " ")
        .trim();
    while (isWrappedByWholeParentheses(normalized)) {
      normalized = normalized.substring(1, normalized.length() - 1).trim();
    }
    return normalized.replaceAll("\\s+", " ");
  }

  private static MatchBody rootMatchBody(String formula) {
    int bodyStart = firstNonWhitespace(formula);
    if (bodyStart < 0) {
      return null;
    }
    if (!(formula.startsWith("match{", bodyStart) || formula.startsWith("match {", bodyStart))) {
      return null;
    }
    int openBrace = formula.indexOf('{', bodyStart);
    int closeBrace = findMatchingBrace(formula, openBrace);
    if (openBrace < 0 || closeBrace <= openBrace) {
      return null;
    }
    return new MatchBody(formula.substring(openBrace + 1, closeBrace), openBrace + 1);
  }

  private static int firstNonWhitespace(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        return i;
      }
    }
    return -1;
  }

  private static Segment extractCaseValue(Segment caseClause) {
    if (caseClause == null || caseClause.text().isBlank()) {
      return null;
    }
    int arrow = findTopLevelArrow(caseClause.text());
    if (arrow < 0) {
      return null;
    }
    int relativeStart = arrow + 2;
    int localStart = trimLeadingIndex(caseClause.text(), relativeStart);
    int localEnd = trimTrailingIndex(caseClause.text(), localStart, caseClause.text().length());
    if (localStart >= localEnd) {
      return null;
    }
    return new Segment(
        caseClause.text().substring(localStart, localEnd),
        caseClause.startOffset() + localStart,
        caseClause.startOffset() + localEnd);
  }

  private static int trimLeadingIndex(String text, int start) {
    int index = Math.max(0, start);
    while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
      index++;
    }
    return index;
  }

  private static int trimTrailingIndex(String text, int start, int end) {
    int index = Math.min(text.length(), Math.max(start, end));
    while (index > start && Character.isWhitespace(text.charAt(index - 1))) {
      index--;
    }
    return index;
  }

  private static int findTopLevelArrow(String text) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length() - 1; i++) {
      char c = text.charAt(i);
      char next = text.charAt(i + 1);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && c == '-' && next == '>') {
        return i;
      }
    }
    return -1;
  }

  private static int findMatchingBrace(String text, int openBrace) {
    if (openBrace < 0 || openBrace >= text.length() || text.charAt(openBrace) != '{') {
      return -1;
    }
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = openBrace; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<Segment> splitTopLevel(String text, char separator, int baseOffset) {
    java.util.ArrayList<Segment> parts = new java.util.ArrayList<>();
    int start = 0;
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (c == separator && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
        addSegment(parts, text, start, i, baseOffset);
        start = i + 1;
      }
    }
    addSegment(parts, text, start, text.length(), baseOffset);
    return parts;
  }

  private static void addSegment(List<Segment> parts, String text, int rawStart, int rawEnd, int baseOffset) {
    int start = trimLeadingIndex(text, rawStart);
    int end = trimTrailingIndex(text, start, rawEnd);
    parts.add(new Segment(text.substring(start, end), baseOffset + start, baseOffset + end));
  }

  private static boolean isWrappedByWholeParentheses(String text) {
    if (text == null || text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
      return false;
    }
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
      } else if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0 && i < text.length() - 1) {
          return false;
        }
      }
    }
    return depth == 0;
  }

  private enum ExpectedType {
    NUMBER("number") {
      @Override
      boolean accepts(String inlineHint) {
        return "number".equals(inlineHint) || "float".equals(inlineHint);
      }
    },
    STRING("string") {
      @Override
      boolean accepts(String inlineHint) {
        return "string".equals(inlineHint);
      }
    },
    BOOLEAN("boolean") {
      @Override
      boolean accepts(String inlineHint) {
        return "boolean".equals(inlineHint);
      }
    };

    final String label;

    ExpectedType(String label) {
      this.label = label;
    }

    abstract boolean accepts(String inlineHint);

    static ExpectedType from(ExpressionType resultType) {
      if (resultType == ExpressionTypes.string) {
        return STRING;
      }
      if (resultType == ExpressionTypes._boolean) {
        return BOOLEAN;
      }
      if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
        return NUMBER;
      }
      return null;
    }
  }

  public record Violation(
      String message,
      int startOffset,
      int endOffset,
      ViolationKind kind,
      String snippet) {
    public int length() {
      return Math.max(1, endOffset - startOffset);
    }
  }

  public enum ViolationKind {
    DIRECT_VARIABLE_CASE_VALUE,
    DIRECT_METHOD_INVOCATION
  }

  private record Segment(String text, int startOffset, int endOffset) {}

  private record MatchBody(String body, int bodyStartOffset) {}
}
