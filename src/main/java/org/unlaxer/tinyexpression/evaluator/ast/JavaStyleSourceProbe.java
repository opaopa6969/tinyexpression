package org.unlaxer.tinyexpression.evaluator.ast;

import org.unlaxer.tinyexpression.parser.TinyExpressionKeywords;

final class JavaStyleSourceProbe {

  private JavaStyleSourceProbe() {}

  static int skipDelimiters(String source, int from) {
    if (source == null || source.isEmpty()) {
      return 0;
    }
    int i = Math.max(0, from);
    while (i < source.length()) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      if (c == '/' && i + 1 < source.length()) {
        char next = source.charAt(i + 1);
        if (next == '/') {
          i += 2;
          while (i < source.length() && source.charAt(i) != '\n') {
            i++;
          }
          continue;
        }
        if (next == '*') {
          int end = source.indexOf("*/", i + 2);
          if (end < 0) {
            return source.length();
          }
          i = end + 2;
          continue;
        }
      }
      break;
    }
    return i;
  }

  static String trimLeadingDelimiters(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    int index = skipDelimiters(source, 0);
    if (index <= 0) {
      return source;
    }
    if (index >= source.length()) {
      return "";
    }
    return source.substring(index);
  }

  static boolean containsCommentAfterLeadingDelimiters(String source) {
    String trimmed = trimLeadingDelimiters(source);
    if (trimmed.isEmpty()) {
      return false;
    }
    int block = trimmed.indexOf("/*");
    int line = trimmed.indexOf("//");
    return block >= 0 || line >= 0;
  }

  static boolean startsWithWord(String source, String word) {
    if (source == null || word == null || word.isEmpty()) {
      return false;
    }
    String text = source.stripLeading();
    return matchesWordAt(text, 0, word);
  }

  static boolean matchesWordAt(String source, int index, String word) {
    if (source == null || word == null || word.isEmpty()) {
      return false;
    }
    if (index < 0 || index + word.length() > source.length()) {
      return false;
    }
    if (!source.startsWith(word, index)) {
      return false;
    }
    if (index > 0 && Character.isJavaIdentifierPart(source.charAt(index - 1))) {
      return false;
    }
    int end = index + word.length();
    return end >= source.length() || !Character.isJavaIdentifierPart(source.charAt(end));
  }

  static boolean hasHead(String source, String keyword, Character requiredNext) {
    if (source == null || keyword == null || keyword.isEmpty()) {
      return false;
    }
    String text = source.stripLeading();
    if (!matchesWordAt(text, 0, keyword)) {
      return false;
    }
    if (requiredNext == null) {
      return true;
    }
    int next = skipDelimiters(text, keyword.length());
    return next < text.length() && text.charAt(next) == requiredNext;
  }

  static String normalizeStructuredHead(String source) {
    String text = trimLeadingDelimiters(source).stripLeading();
    if (text.isEmpty()) {
      return text;
    }
    if (hasHead(text, TinyExpressionKeywords.IF, '(')) {
      int next = skipDelimiters(text, TinyExpressionKeywords.IF.length());
      return TinyExpressionKeywords.IF + text.substring(next);
    }
    for (String keyword : TinyExpressionKeywords.METHOD_INVOCATION_HEADS) {
      if (!startsWithWord(text, keyword)) {
        continue;
      }
      int next = skipDelimiters(text, keyword.length());
      if (next >= text.length()) {
        return keyword;
      }
      return keyword + " " + text.substring(next).stripLeading();
    }
    return text;
  }
}
