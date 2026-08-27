package org.unlaxer.tinyexpression.p4;

import java.util.Optional;

public final class P4TernarySourceSupport {

  public record TernaryParts(
      String conditionSource,
      String thenSource,
      String elseSource) {}

  private P4TernarySourceSupport() {}

  public static Optional<TernaryParts> parseTopLevelTernary(String sourceText) {
    if (sourceText == null) {
      return Optional.empty();
    }
    String source = unwrapWholeParentheses(sourceText.strip());
    if (source.isEmpty()) {
      return Optional.empty();
    }
    int questionIndex = findTopLevelQuestion(source);
    if (questionIndex < 0) {
      return Optional.empty();
    }
    int colonIndex = findMatchingColon(source, questionIndex);
    if (colonIndex < 0) {
      return Optional.empty();
    }
    String condition = source.substring(0, questionIndex).strip();
    String thenExpr = source.substring(questionIndex + 1, colonIndex).strip();
    String elseExpr = source.substring(colonIndex + 1).strip();
    if (condition.isEmpty() || thenExpr.isEmpty() || elseExpr.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new TernaryParts(condition, thenExpr, elseExpr));
  }

  private static int findTopLevelQuestion(String source) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
      if (inLineComment) {
        if (c == '\n') {
          inLineComment = false;
        }
        continue;
      }
      if (inBlockComment) {
        if (c == '*' && next == '/') {
          i++;
          inBlockComment = false;
        }
        continue;
      }
      if (inSingleQuote) {
        if (c == '\'' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inSingleQuote = false;
        }
        continue;
      }
      if (inDoubleQuote) {
        if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inDoubleQuote = false;
        }
        continue;
      }
      if (c == '/' && next == '/') {
        inLineComment = true;
        i++;
        continue;
      }
      if (c == '/' && next == '*') {
        inBlockComment = true;
        i++;
        continue;
      }
      if (c == '\'') {
        inSingleQuote = true;
        continue;
      }
      if (c == '"') {
        inDoubleQuote = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '?' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            return i;
          }
        }
        default -> {
        }
      }
    }
    return -1;
  }

  private static int findMatchingColon(String source, int questionIndex) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    int nestedTernaryDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    for (int i = questionIndex + 1; i < source.length(); i++) {
      char c = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
      if (inLineComment) {
        if (c == '\n') {
          inLineComment = false;
        }
        continue;
      }
      if (inBlockComment) {
        if (c == '*' && next == '/') {
          i++;
          inBlockComment = false;
        }
        continue;
      }
      if (inSingleQuote) {
        if (c == '\'' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inSingleQuote = false;
        }
        continue;
      }
      if (inDoubleQuote) {
        if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inDoubleQuote = false;
        }
        continue;
      }
      if (c == '/' && next == '/') {
        inLineComment = true;
        i++;
        continue;
      }
      if (c == '/' && next == '*') {
        inBlockComment = true;
        i++;
        continue;
      }
      if (c == '\'') {
        inSingleQuote = true;
        continue;
      }
      if (c == '"') {
        inDoubleQuote = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth = Math.max(0, parenDepth - 1);
        case '{' -> braceDepth++;
        case '}' -> braceDepth = Math.max(0, braceDepth - 1);
        case '[' -> bracketDepth++;
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        case '?' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            nestedTernaryDepth++;
          }
        }
        case ':' -> {
          if (parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
            if (nestedTernaryDepth == 0) {
              return i;
            }
            nestedTernaryDepth--;
          }
        }
        default -> {
        }
      }
    }
    return -1;
  }

  private static String unwrapWholeParentheses(String source) {
    String current = source == null ? "" : source.strip();
    while (isWrappedByWholeParentheses(current)) {
      current = current.substring(1, current.length() - 1).strip();
    }
    return current;
  }

  private static boolean isWrappedByWholeParentheses(String source) {
    if (source == null || source.length() < 2 || source.charAt(0) != '(' || source.charAt(source.length() - 1) != ')') {
      return false;
    }
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '\'' && !inDoubleQuote && (i == 0 || source.charAt(i - 1) != '\\')) {
        inSingleQuote = !inSingleQuote;
        continue;
      }
      if (c == '"' && !inSingleQuote && (i == 0 || source.charAt(i - 1) != '\\')) {
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (inSingleQuote || inDoubleQuote) {
        continue;
      }
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0 && i < source.length() - 1) {
          return false;
        }
      }
    }
    return depth == 0;
  }
}
