package org.unlaxer.tinyexpression.p4;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4AST;
import org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper;

/**
 * Selects a more specific generated AST root when the generic mapper would
 * otherwise settle on a shallow wrapper such as {@code ExpressionExpr}.
 */
public final class P4PreferredAstMapper {

  private P4PreferredAstMapper() {}

  public record ParsedAst(TinyExpressionP4AST ast, String selectionMode) {}

  public static TinyExpressionP4AST parse(String formula) {
    return parseDetailed(formula).ast();
  }

  public static ParsedAst parseDetailed(String formula) {
    IllegalArgumentException preferredFailure = null;
    for (String preferredAstSimpleName : preferredAstSimpleNames(formula)) {
      try {
        TinyExpressionP4AST ast = TinyExpressionP4Mapper.parse(formula, preferredAstSimpleName);
        if (ast != null && preferredAstSimpleName.equals(ast.getClass().getSimpleName())) {
          return new ParsedAst(ast, "preferred:" + preferredAstSimpleName);
        }
      } catch (IllegalArgumentException e) {
        preferredFailure = e;
      }
    }

    try {
      return new ParsedAst(TinyExpressionP4Mapper.parse(formula), "default");
    } catch (IllegalArgumentException e) {
      if (preferredFailure != null) {
        throw preferredFailure;
      }
      throw e;
    }
  }

  static List<String> preferredAstSimpleNames(String formula) {
    if (formula == null) {
      return List.of();
    }
    String normalized = formula.strip();
    if (normalized.isEmpty()) {
      return List.of();
    }

    LinkedHashSet<String> names = new LinkedHashSet<>();
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.startsWith("match{") || lower.startsWith("match {")) {
      names.addAll(preferredMatchAstSimpleNames(normalized));
    }
    if (lower.startsWith("if(") || lower.startsWith("if (")) {
      names.add("IfExpr");
    }
    return List.copyOf(names);
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
    names.add("NumberMatchExpr");
    names.add("StringMatchExpr");
    names.add("BooleanMatchExpr");
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
    int index = Math.min(text.length(), end);
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

  private record MatchBody(String body, int bodyStartOffset) {}

  private record Segment(String text, int startOffset, int endOffset) {}
}
