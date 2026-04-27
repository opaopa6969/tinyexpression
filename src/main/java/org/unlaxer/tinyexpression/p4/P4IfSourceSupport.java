package org.unlaxer.tinyexpression.p4;

import java.util.Optional;

import org.unlaxer.tinyexpression.parser.TinyExpressionParserCapabilities;

public final class P4IfSourceSupport {

  public record IfParts(
      String conditionSource,
      String thenSource,
      String elseSource) {}

  private P4IfSourceSupport() {}

  public static Optional<IfParts> ifPartsOfNode(Object node, String sourceFormula) {
    return P4SliceSourceSupport.sourceSnippetOfNode(node, sourceFormula).flatMap(P4IfSourceSupport::parseIfSnippet);
  }

  public static Optional<IfParts> parseIfSnippet(String ifSource) {
    if (ifSource == null) {
      return Optional.empty();
    }
    String source = ifSource.strip();
    if (source.isEmpty()) {
      return Optional.empty();
    }
    int cursor = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, 0);
    if (!TinyExpressionParserCapabilities.matchesWordAt(source, cursor, "if")) {
      return Optional.empty();
    }
    cursor = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, cursor + 2);
    if (cursor >= source.length() || source.charAt(cursor) != '(') {
      return Optional.empty();
    }
    int closeParen = findMatching(source, cursor, '(', ')');
    if (closeParen <= cursor) {
      return Optional.empty();
    }
    String conditionSource = source.substring(cursor + 1, closeParen).strip();
    cursor = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, closeParen + 1);
    if (cursor >= source.length() || source.charAt(cursor) != '{') {
      return Optional.empty();
    }
    int closeThenBrace = findMatching(source, cursor, '{', '}');
    if (closeThenBrace <= cursor) {
      return Optional.empty();
    }
    String thenSource = source.substring(cursor + 1, closeThenBrace).strip();
    cursor = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, closeThenBrace + 1);
    if (!TinyExpressionParserCapabilities.matchesWordAt(source, cursor, "else")) {
      return Optional.empty();
    }
    cursor = TinyExpressionParserCapabilities.skipJavaStyleDelimiters(source, cursor + 4);
    if (cursor >= source.length() || source.charAt(cursor) != '{') {
      return Optional.empty();
    }
    int closeElseBrace = findMatching(source, cursor, '{', '}');
    if (closeElseBrace <= cursor) {
      return Optional.empty();
    }
    String elseSource = source.substring(cursor + 1, closeElseBrace).strip();
    if (conditionSource.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new IfParts(conditionSource, thenSource, elseSource));
  }

  private static int findMatching(String source, int openIndex, char open, char close) {
    if (openIndex < 0 || openIndex >= source.length() || source.charAt(openIndex) != open) {
      return -1;
    }
    int depth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    boolean inLineComment = false;
    boolean inBlockComment = false;
    for (int i = openIndex; i < source.length(); i++) {
      char current = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
      if (inLineComment) {
        if (current == '\n') {
          inLineComment = false;
        }
        continue;
      }
      if (inBlockComment) {
        if (current == '*' && next == '/') {
          i++;
          inBlockComment = false;
        }
        continue;
      }
      if (inSingleQuote) {
        if (current == '\'' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inSingleQuote = false;
        }
        continue;
      }
      if (inDoubleQuote) {
        if (current == '"' && (i == 0 || source.charAt(i - 1) != '\\')) {
          inDoubleQuote = false;
        }
        continue;
      }
      if (current == '/' && next == '/') {
        inLineComment = true;
        i++;
        continue;
      }
      if (current == '/' && next == '*') {
        inBlockComment = true;
        i++;
        continue;
      }
      if (current == '\'') {
        inSingleQuote = true;
        continue;
      }
      if (current == '"') {
        inDoubleQuote = true;
        continue;
      }
      if (current == open) {
        depth++;
      } else if (current == close) {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }
}
