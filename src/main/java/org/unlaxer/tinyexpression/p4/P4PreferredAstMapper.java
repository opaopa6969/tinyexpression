package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.unlaxer.Parsed;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

/**
 * Selects a more specific generated AST root when the generic mapper would
 * otherwise settle on a shallow wrapper such as {@code ExpressionExpr}.
 */
public final class P4PreferredAstMapper {

  private static final String IF_KEYWORD = "if";
  private static final String MATCH_KEYWORD = "match";
  private static final List<String> METHOD_INVOCATION_HEADS = List.of("call", "external", "internal");

  private static final List<String> MATCH_AST_SIMPLE_NAMES = List.of(
      "NumberMatchExpr",
      "StringMatchExpr",
      "BooleanMatchExpr");
  private static final Map<String, String> FUNCTION_AST_NAMES = Map.ofEntries(
      Map.entry("sin", "SinExpr"),
      Map.entry("cos", "CosExpr"),
      Map.entry("tan", "TanExpr"),
      Map.entry("sqrt", "SqrtExpr"),
      Map.entry("min", "MinExpr"),
      Map.entry("max", "MaxExpr"),
      Map.entry("random", "RandomExpr"),
      Map.entry("abs", "AbsExpr"),
      Map.entry("round", "RoundExpr"),
      Map.entry("ceil", "CeilExpr"),
      Map.entry("floor", "FloorExpr"),
      Map.entry("pow", "PowExpr"),
      Map.entry("log", "LogExpr"),
      Map.entry("exp", "ExpExpr"),
      Map.entry("toNum", "ToNumExpr"),
      Map.entry("toUpperCase", "ToUpperCaseExpr"),
      Map.entry("toLowerCase", "ToLowerCaseExpr"),
      Map.entry("trim", "TrimExpr"),
      Map.entry("len", "LengthExpr"),
      Map.entry("length", "LengthExpr"),
      Map.entry("startsWith", "StartsWithExpr"),
      Map.entry("endsWith", "EndsWithExpr"),
      Map.entry("contains", "ContainsExpr"),
      Map.entry("isPresent", "IsPresentExpr"),
      Map.entry("inTimeRange", "InTimeRangeExpr"),
      Map.entry("inDayTimeRange", "InDayTimeRangeExpr"));
  private static final Map<String, String> DOT_METHOD_AST_NAMES = Map.ofEntries(
      Map.entry("toUpperCase", "ToUpperCaseDotExpr"),
      Map.entry("toLowerCase", "ToLowerCaseDotExpr"),
      Map.entry("trim", "TrimDotExpr"),
      Map.entry("length", "LengthDotExpr"),
      Map.entry("startsWith", "StartsWithDotExpr"),
      Map.entry("endsWith", "EndsWithDotExpr"),
      Map.entry("contains", "ContainsDotExpr"),
      Map.entry("in", "InExpr"));

  private P4PreferredAstMapper() {}

  public record ParsedAst(TinyExpressionP4AST ast, String selectionMode) {}

  public static TinyExpressionP4AST parse(String formula) {
    return parseDetailed(formula, null).ast();
  }

  public static TinyExpressionP4AST parse(String formula, ExpressionType preferredResultType) {
    return parseDetailed(formula, preferredResultType).ast();
  }

  public static ParsedAst parseDetailed(String formula) {
    return parseDetailed(formula, null);
  }

  public static ParsedAst parseDetailed(String formula, ExpressionType preferredResultType) {
    RuntimeException preferredFailure = null;
    RuntimeException defaultFailure = null;
    for (String candidateSource : candidateFormulaSources(formula)) {
      for (String preferredAstSimpleName : preferredAstSimpleNames(candidateSource, preferredResultType)) {
        try {
          TinyExpressionP4AST ast = parseViaMapperCompat(candidateSource, preferredAstSimpleName);
          if (ast != null && preferredAstSimpleName.equals(ast.getClass().getSimpleName())) {
            return new ParsedAst(ast, "preferred:" + preferredAstSimpleName);
          }
        } catch (RuntimeException e) {
          preferredFailure = e;
        }
      }

      try {
        return new ParsedAst(parseViaMapperCompat(candidateSource, null), "default");
      } catch (RuntimeException e) {
        defaultFailure = e;
      }
    }

    if (preferredFailure != null) {
      throw toParseFailure(preferredFailure);
    }
    if (defaultFailure != null) {
      throw toParseFailure(defaultFailure);
    }
    throw new IllegalArgumentException("Parse failed: " + formula);
  }

  public static String normalizeExpressionSnippetForParsing(String formula) {
    if (formula == null) {
      return "";
    }
    String normalized = formula.strip();
    if (normalized.isEmpty()) {
      return normalized;
    }
    if (hasTopLevelTernary(normalized) && !isWrappedByWholeParentheses(normalized)) {
      return "(" + normalized + ")";
    }
    return normalized;
  }

  public static List<String> preferredAstSimpleNames(String formula) {
    return preferredAstSimpleNames(formula, null);
  }

  public static List<String> preferredAstSimpleNames(String formula, ExpressionType preferredResultType) {
    if (formula == null) {
      return List.of();
    }
    String normalized = normalizeForPreferredParsing(formula);
    if (normalized.isEmpty()) {
      return List.of();
    }

    ArrayList<String> names = new ArrayList<>();
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.startsWith("match{") || lower.startsWith("match {")) {
      addIfAbsent(names, preferredMatchAstSimpleName(preferredResultType));
      for (String candidate : preferredMatchAstSimpleNames(normalized)) {
        addIfAbsent(names, candidate);
      }
      for (String candidate : MATCH_AST_SIMPLE_NAMES) {
        addIfAbsent(names, candidate);
      }
    }
    if (lower.startsWith("if(") || lower.startsWith("if (")) {
      addIfAbsent(names, "IfExpr");
    }
    if (hasTopLevelTernary(normalized)) {
      addIfAbsent(names, "IfExpr");
    }
    addIfAbsent(names, functionAstSimpleName(normalized));
    addIfAbsent(names, dotMethodAstSimpleName(normalized));
    if (looksLikeSliceExpression(normalized)) {
      addIfAbsent(names, "SliceExpr");
    }
    return List.copyOf(names);
  }

  public static List<String> astEvaluatorCandidateAstSimpleNames(String formula, ExpressionType preferredResultType) {
    return candidateAstSimpleNames(formula, preferredResultType, CandidateProfile.AST_EVALUATOR);
  }

  public static List<String> generatedValueCandidateAstSimpleNames(String formula, ExpressionType preferredResultType) {
    return candidateAstSimpleNames(formula, preferredResultType, CandidateProfile.GENERATED_VALUE);
  }

  public static List<String> declarationCandidateAstSimpleNames(String formula, ExpressionType preferredResultType) {
    return candidateAstSimpleNames(formula, preferredResultType, CandidateProfile.DECLARATION);
  }

  private static List<String> candidateFormulaSources(String formula) {
    if (formula == null) {
      return List.of("");
    }
    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    String stripped = formula.strip();
    candidates.add(stripped);

    String trimmed = TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(formula).stripLeading();
    if (!trimmed.equals(stripped)) {
      candidates.add(trimmed);
    }

    String withoutComments =
        TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(formula).strip();
    if (!withoutComments.equals(stripped)) {
      candidates.add(withoutComments);
    }

    String trimmedWithoutComments =
        TinyExpressionParserCapabilities.trimLeadingJavaStyleDelimiters(
            TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(formula)).stripLeading();
    if (!trimmedWithoutComments.equals(stripped)) {
      candidates.add(trimmedWithoutComments);
    }

    String normalized = normalizeForPreferredParsing(formula);
    if (!normalized.equals(stripped)) {
      candidates.add(normalized);
    }

    String normalizedWithoutComments = normalizeForPreferredParsing(
        TinyExpressionParserCapabilities.stripJavaStyleCommentsPreservingLayout(formula));
    if (!normalizedWithoutComments.equals(stripped)) {
      candidates.add(normalizedWithoutComments);
    }
    return List.copyOf(candidates);
  }

  private static String normalizeForPreferredParsing(String formula) {
    if (formula == null) {
      return "";
    }
    return TinyExpressionParserCapabilities.normalizeStructuredHead(formula).strip();
  }

  private static List<String> candidateAstSimpleNames(
      String formula, ExpressionType preferredResultType, CandidateProfile profile) {
    String normalized = normalizeForPreferredParsing(formula);
    List<String> structuredPreferred = preferredAstSimpleNames(normalized, preferredResultType);
    if (!structuredPreferred.isEmpty()) {
      return structuredPreferred;
    }

    ArrayList<String> names = new ArrayList<>();
    boolean methodInvocationHead = hasMethodInvocationHead(normalized);
    boolean ifHead = hasIfHead(normalized);
    boolean matchHead = hasMatchHead(normalized);
    if (methodInvocationHead) {
      addIfAbsent(names, "MethodInvocationExpr");
    }
    if (ifHead) {
      addIfAbsent(names, "IfExpr");
    }
    String functionAstSimpleName = functionAstSimpleName(normalized);
    if (functionAstSimpleName != null) {
      addIfAbsent(names, functionAstSimpleName);
    }
    String dotMethodAstSimpleName = dotMethodAstSimpleName(normalized);
    if (dotMethodAstSimpleName != null) {
      addIfAbsent(names, dotMethodAstSimpleName);
    }
    if (looksLikeSliceExpression(normalized)) {
      addIfAbsent(names, "SliceExpr");
    }
    if (preferredResultType == null) {
      names.add(null);
      return names.stream().distinct().toList();
    }

    if (preferredResultType.isNumber()) {
      if (matchHead) {
        addIfAbsent(names, "NumberMatchExpr");
      }
      addIfAbsent(names, "BinaryExpr");
    } else if (preferredResultType.isString()) {
      if (matchHead) {
        addIfAbsent(names, "StringMatchExpr");
      }
      addIfAbsent(names, "StringConcatExpr");
    } else if (preferredResultType.isBoolean()) {
      if (matchHead) {
        addIfAbsent(names, "BooleanMatchExpr");
      }
      addIfAbsent(names, profile == CandidateProfile.DECLARATION ? "BooleanExpr" : "BooleanOrExpr");
    } else if (preferredResultType.isObject()) {
      if (matchHead) {
        addIfAbsent(names, "StringMatchExpr");
        addIfAbsent(names, "BooleanMatchExpr");
        addIfAbsent(names, "NumberMatchExpr");
      }
      addIfAbsent(names, "ObjectExpr");
      if (profile == CandidateProfile.GENERATED_VALUE) {
        addIfAbsent(names, "StringConcatExpr");
        addIfAbsent(names, "BooleanOrExpr");
        addIfAbsent(names, "BinaryExpr");
      }
    } else {
      names.add(null);
    }

    addIfAbsent(names, "MethodInvocationExpr");
    addIfAbsent(names, "VariableRefExpr");
    if (profile == CandidateProfile.GENERATED_VALUE) {
      addIfAbsent(names, "IfExpr");
    }
    addIfAbsent(names, "BinaryExpr");
    if (profile != CandidateProfile.GENERATED_VALUE) {
      names.add(null);
    }
    return names.stream().distinct().toList();
  }

  private static boolean hasIfHead(String normalized) {
    return TinyExpressionParserCapabilities.hasHead(normalized, IF_KEYWORD, '(');
  }

  private static boolean hasMatchHead(String normalized) {
    return TinyExpressionParserCapabilities.hasHead(normalized, MATCH_KEYWORD, '{');
  }

  private static boolean hasMethodInvocationHead(String normalized) {
    for (String keyword : METHOD_INVOCATION_HEADS) {
      if (TinyExpressionParserCapabilities.hasHead(normalized, keyword, null)) {
        return true;
      }
    }
    return false;
  }

  private static String functionAstSimpleName(String normalized) {
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    int parenIdx = normalized.indexOf('(');
    if (parenIdx <= 0) {
      return null;
    }
    int closeParen = findMatchingParen(normalized, parenIdx);
    if (closeParen != normalized.length() - 1) {
      return null;
    }
    String head = normalized.substring(0, parenIdx).strip();
    return FUNCTION_AST_NAMES.get(head);
  }

  private static String dotMethodAstSimpleName(String normalized) {
    if (normalized == null || normalized.isEmpty()) {
      return null;
    }
    int parenIdx = normalized.indexOf('(');
    if (parenIdx <= 0) {
      return null;
    }
    int closeParen = findMatchingParen(normalized, parenIdx);
    if (closeParen != normalized.length() - 1) {
      return null;
    }
    int dotIdx = findLastTopLevelDotBefore(normalized, parenIdx);
    if (dotIdx <= 0) {
      return null;
    }
    String methodName = normalized.substring(dotIdx + 1, parenIdx).strip();
    return DOT_METHOD_AST_NAMES.get(methodName);
  }

  private static int findLastTopLevelDotBefore(String text, int beforeIndex) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    int dotIdx = -1;
    int limit = Math.min(beforeIndex, text.length());
    for (int i = 0; i < limit; i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
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
        case '.' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            dotIdx = i;
          }
        }
        default -> {
        }
      }
    }
    return dotIdx;
  }

  private static int findMatchingParen(String text, int openIndex) {
    if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != '(') {
      return -1;
    }
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = openIndex; i < text.length(); i++) {
      char c = text.charAt(i);
      char prev = i > 0 ? text.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> {
          parenDepth--;
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            return i;
          }
        }
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
    }
    return -1;
  }

  private static boolean looksLikeSliceExpression(String normalized) {
    if (normalized == null || normalized.isEmpty() || normalized.charAt(normalized.length() - 1) != ']') {
      return false;
    }
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    int sliceOpenIndex = -1;
    boolean sawColonInside = false;
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      char prev = i > 0 ? normalized.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            sliceOpenIndex = i;
            sawColonInside = false;
          }
          bracketDepth++;
        }
        case ']' -> {
          bracketDepth = Math.max(0, bracketDepth - 1);
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0
              && sliceOpenIndex >= 0 && i == normalized.length() - 1) {
            return sawColonInside;
          }
        }
        case ':' -> {
          if (sliceOpenIndex >= 0 && bracketDepth > 0) {
            sawColonInside = true;
          }
        }
        default -> {
        }
      }
    }
    return false;
  }

  private enum CandidateProfile {
    AST_EVALUATOR,
    GENERATED_VALUE,
    DECLARATION
  }

  private static boolean hasTopLevelTernary(String formula) {
    if (isWrappedByWholeParentheses(formula)) {
      String inner = formula.substring(1, formula.length() - 1).trim();
      if (!inner.isEmpty() && hasTopLevelTernary(inner)) {
        return true;
      }
    }
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean sawQuestion = false;
    for (int i = 0; i < formula.length(); i++) {
      char c = formula.charAt(i);
      char prev = i > 0 ? formula.charAt(i - 1) : '\0';
      if (c == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
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
      if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
        if (c == '?') {
          sawQuestion = true;
        } else if (c == ':' && sawQuestion) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isWrappedByWholeParentheses(String text) {
    if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
      return false;
    }
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
        continue;
      }
      if (c == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> {
          parenDepth--;
          if (parenDepth == 0 && i < text.length() - 1) {
            return false;
          }
        }
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
      if (parenDepth < 0 || braceDepth < 0 || bracketDepth < 0) {
        return false;
      }
    }
    return parenDepth == 0 && braceDepth == 0 && bracketDepth == 0;
  }

  private static void addIfAbsent(List<String> names, String candidate) {
    if (candidate != null && !candidate.isBlank() && !names.contains(candidate)) {
      names.add(candidate);
    }
  }

  private static String preferredMatchAstSimpleName(ExpressionType resultType) {
    if (resultType == ExpressionTypes.string) {
      return "StringMatchExpr";
    }
    if (resultType == ExpressionTypes._boolean) {
      return "BooleanMatchExpr";
    }
    if (resultType instanceof ExpressionTypes expressionTypes && expressionTypes.isNumber()) {
      return "NumberMatchExpr";
    }
    return null;
  }

  private static List<String> preferredMatchAstSimpleNames(String formula) {
    LinkedHashSet<String> names = new LinkedHashSet<>();
    MatchBody matchBody = rootMatchBody(formula);
    if (matchBody != null) {
      for (Segment clause : splitTopLevel(matchBody.body(), ',', matchBody.bodyStartOffset())) {
        Segment value = extractCaseValue(clause);
        if (value == null) {
          continue;
        }
        String normalized = normalizeCaseValue(value.text()).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
          continue;
        }
        if (looksLikeStringCaseValue(normalized)) {
          names.add("StringMatchExpr");
        }
        if (looksLikeBooleanCaseValue(normalized)) {
          names.add("BooleanMatchExpr");
        }
        if (looksLikeNumberCaseValue(normalized)) {
          names.add("NumberMatchExpr");
        }
      }
    }
    return List.copyOf(names);
  }

  private static boolean looksLikeStringCaseValue(String normalized) {
    return isQuoted(normalized)
        || normalized.contains(" as string")
        || normalized.startsWith("touppercase(")
        || normalized.startsWith("tolowercase(")
        || normalized.startsWith("trim(")
        || normalized.startsWith("slice(")
        || normalized.contains(".touppercase(")
        || normalized.contains(".tolowercase(")
        || normalized.contains(".trim(")
        || normalized.contains(".slice(");
  }

  private static boolean looksLikeBooleanCaseValue(String normalized) {
    return "true".equals(normalized)
        || "false".equals(normalized)
        || normalized.contains(" as boolean")
        || normalized.startsWith("not ")
        || normalized.startsWith("!")
        || normalized.contains("==")
        || normalized.contains("!=")
        || normalized.contains("<=")
        || normalized.contains(">=")
        || normalized.contains("&&")
        || normalized.contains("||")
        || normalized.contains("&")
        || normalized.contains("|");
  }

  private static boolean looksLikeNumberCaseValue(String normalized) {
    return normalized.matches("[-+]?\\d+(?:\\.\\d+)?")
        || normalized.contains(" as number")
        || normalized.contains(" as float")
        || normalized.contains("+")
        || normalized.contains("-")
        || normalized.contains("*")
        || normalized.contains("/")
        || normalized.startsWith("sin(")
        || normalized.startsWith("cos(")
        || normalized.startsWith("tan(")
        || normalized.startsWith("sqrt(")
        || normalized.startsWith("min(")
        || normalized.startsWith("max(")
        || normalized.startsWith("abs(")
        || normalized.startsWith("round(")
        || normalized.startsWith("ceil(")
        || normalized.startsWith("floor(")
        || normalized.startsWith("pow(")
        || normalized.startsWith("log(")
        || normalized.startsWith("exp(")
        || normalized.startsWith("random(")
        || normalized.startsWith("tonum(");
  }

  private static boolean isQuoted(String normalized) {
    return normalized.length() >= 2
        && ((normalized.charAt(0) == '\'' && normalized.charAt(normalized.length() - 1) == '\'')
            || (normalized.charAt(0) == '"' && normalized.charAt(normalized.length() - 1) == '"'));
  }

  private static String normalizeCaseValue(String snippet) {
    if (snippet == null) {
      return "";
    }
    return snippet.strip();
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

  private static Segment extractCaseValue(Segment clause) {
    if (clause == null || clause.text().isBlank()) {
      return null;
    }
    int arrow = findTopLevelArrow(clause.text());
    if (arrow < 0) {
      return null;
    }
    int localStart = trimLeadingIndex(clause.text(), arrow + 2);
    int localEnd = trimTrailingIndex(clause.text(), localStart, clause.text().length());
    if (localStart >= localEnd) {
      return null;
    }
    return new Segment(
        clause.text().substring(localStart, localEnd),
        clause.startOffset() + localStart,
        clause.startOffset() + localEnd);
  }

  private static List<Segment> splitTopLevel(String text, char separator, int baseOffset) {
    ArrayList<Segment> parts = new ArrayList<>();
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
      if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0 && c == separator) {
        addSegment(parts, text, start, i, baseOffset);
        start = i + 1;
      }
    }
    addSegment(parts, text, start, text.length(), baseOffset);
    return parts;
  }

  private static void addSegment(List<Segment> parts, String text, int start, int end, int baseOffset) {
    int localStart = trimLeadingIndex(text, start);
    int localEnd = trimTrailingIndex(text, localStart, end);
    if (localStart >= localEnd) {
      return;
    }
    parts.add(new Segment(
        text.substring(localStart, localEnd),
        baseOffset + localStart,
        baseOffset + localEnd));
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

  private static IllegalArgumentException toParseFailure(RuntimeException failure) {
    if (failure instanceof IllegalArgumentException illegalArgumentException) {
      return illegalArgumentException;
    }
    return new IllegalArgumentException(failure.getMessage(), failure);
  }

  private static TinyExpressionP4AST parseViaMapperCompat(String source, String preferredAstSimpleName) {
    ParseContext context = new ParseContext(createRootSourceCompat(source));
    Parsed parsed;
    try {
      Parser rootParser = TinyExpressionP4Parsers.getRootParser();
      parsed = rootParser.parse(context);
    } finally {
      closeParseContextQuietly(context);
    }
    if (!parsed.isSucceeded()) {
      throw new IllegalArgumentException("Parse failed: " + source);
    }
    int consumed = consumedLengthCompat(parsed.getConsumed());
    if (consumed != source.length()) {
      throw new IllegalArgumentException("Parse failed at offset " + consumed + ": " + source);
    }
    Token rootToken = parsed.getRootToken(true);
    clearMapperSourceSpans();
    Token bestMappedToken = invokeFindBestMappedToken(rootToken, preferredAstSimpleName);
    TinyExpressionP4AST mapped = invokeMapToken(bestMappedToken);
    if (mapped == null) {
      throw new IllegalArgumentException("No mapped node found in parse tree");
    }
    return mapped;
  }

  private static void closeParseContextQuietly(ParseContext context) {
    try {
      context.close();
    } catch (IllegalStateException ignored) {
      // Generated mapper parse can leave nested transactions behind for some
      // formulas even when the root token is still usable.
    }
  }

  private static void clearMapperSourceSpans() {
    try {
      Field field = TinyExpressionP4Mapper.class.getDeclaredField("NODE_SOURCE_SPANS");
      field.setAccessible(true);
      Object value = field.get(null);
      if (value instanceof java.util.Map<?, ?> map) {
        map.clear();
      }
    } catch (ReflectiveOperationException ignored) {
    }
  }

  private static Token invokeFindBestMappedToken(Token rootToken, String preferredAstSimpleName) {
    try {
      Method method = TinyExpressionP4Mapper.class.getDeclaredMethod(
          "findBestMappedToken", Token.class, String.class);
      method.setAccessible(true);
      return (Token) method.invoke(null, rootToken, preferredAstSimpleName);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Failed to resolve mapped token", e);
    }
  }

  private static TinyExpressionP4AST invokeMapToken(Token token) {
    try {
      Method method = TinyExpressionP4Mapper.class.getDeclaredMethod("mapToken", Token.class);
      method.setAccessible(true);
      return (TinyExpressionP4AST) method.invoke(null, token);
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Failed to map parse tree", e);
    }
  }

  private static int consumedLengthCompat(Token token) {
    String text = tokenTextCompat(token);
    return text == null ? 0 : text.length();
  }

  private static String tokenTextCompat(Token token) {
    if (token == null) {
      return null;
    }
    try {
      Method method = token.getClass().getMethod("getToken");
      Object value = method.invoke(token);
      if (value instanceof java.util.Optional<?> optional && optional.isPresent()) {
        Object tokenValue = optional.get();
        return tokenValue == null ? null : String.valueOf(tokenValue);
      }
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      Field field = token.getClass().getField("tokenString");
      Object value = field.get(token);
      if (value instanceof java.util.Optional<?> optional && optional.isPresent()) {
        Object tokenValue = optional.get();
        return tokenValue == null ? null : String.valueOf(tokenValue);
      }
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      Field field = token.getClass().getField("source");
      Object source = field.get(token);
      if (source != null) {
        Method method = source.getClass().getMethod("sourceAsString");
        Object value = method.invoke(source);
        return value == null ? null : String.valueOf(value);
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return null;
  }

  private static StringSource createRootSourceCompat(String source) {
    try {
      Method method = StringSource.class.getMethod("createRootSource", String.class);
      Object value = method.invoke(null, source);
      if (value instanceof StringSource stringSource) {
        return stringSource;
      }
    } catch (ReflectiveOperationException ignored) {
    }
    try {
      for (java.lang.reflect.Constructor<?> constructor : StringSource.class.getDeclaredConstructors()) {
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length == 0 || types[0] != String.class) {
          continue;
        }
        Object[] args = new Object[types.length];
        args[0] = source;
        constructor.setAccessible(true);
        Object value = constructor.newInstance(args);
        if (value instanceof StringSource stringSource) {
          return stringSource;
        }
      }
    } catch (ReflectiveOperationException ignored) {
    }
    throw new IllegalStateException("No compatible StringSource initializer found");
  }

  private record MatchBody(String body, int bodyStartOffset) {}

  private record Segment(String text, int startOffset, int endOffset) {}
}
