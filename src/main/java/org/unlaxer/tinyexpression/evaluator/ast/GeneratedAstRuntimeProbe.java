package org.unlaxer.tinyexpression.evaluator.ast;

import java.lang.reflect.Method;
import java.util.Optional;

final class GeneratedAstRuntimeProbe {

  private GeneratedAstRuntimeProbe() {}

  static boolean isAvailable(ClassLoader classLoader) {
    try {
      Class.forName("org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Parsers", false, classLoader);
      Class.forName("org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      return true;
    } catch (Throwable e) {
      return false;
    }
  }

  static Optional<Object> tryMapAst(String source, ClassLoader classLoader) {
    return tryMapAst(source, classLoader, null);
  }

  static Optional<Object> tryMapAst(String source, ClassLoader classLoader, String preferredAstSimpleName) {
    Optional<Object> mapped = tryMapAstOnce(source, classLoader, preferredAstSimpleName);
    if (mapped.isPresent()) {
      return mapped;
    }
    String normalized = trimLeadingJavaDelimiters(source);
    if (!normalized.equals(source)) {
      mapped = tryMapAstOnce(normalized, classLoader, preferredAstSimpleName);
      if (mapped.isPresent()) {
        return mapped;
      }
    }
    String normalizedHead = normalizeStructuredHead(normalized);
    if (normalizedHead.equals(normalized)) {
      return Optional.empty();
    }
    return tryMapAstOnce(normalizedHead, classLoader, preferredAstSimpleName);
  }

  private static Optional<Object> tryMapAstOnce(String source, ClassLoader classLoader, String preferredAstSimpleName) {
    try {
      Class<?> mapperClass = Class.forName(
          "org.unlaxer.tinyexpression.generated.p4.TinyExpressionP4Mapper", false, classLoader);
      Object ast;
      try {
        Method parsePreferred = mapperClass.getMethod("parse", String.class, String.class);
        ast = parsePreferred.invoke(null, source, preferredAstSimpleName);
      } catch (NoSuchMethodException ignored) {
        Method parse = mapperClass.getMethod("parse", String.class);
        ast = parse.invoke(null, source);
      }
      return Optional.ofNullable(ast);
    } catch (Throwable e) {
      return Optional.empty();
    }
  }

  private static String trimLeadingJavaDelimiters(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    int i = 0;
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
            return "";
          }
          i = end + 2;
          continue;
        }
      }
      break;
    }
    return i == 0 ? source : source.substring(i);
  }

  private static String normalizeStructuredHead(String source) {
    String text = source == null ? "" : source.stripLeading();
    if (text.isEmpty()) {
      return text;
    }
    if (startsWithWord(text, "if")) {
      int next = skipJavaDelimiters(text, "if".length());
      if (next < text.length() && text.charAt(next) == '(') {
        return "if" + text.substring(next);
      }
    }
    for (String keyword : new String[] {"call", "external", "internal"}) {
      if (!startsWithWord(text, keyword)) {
        continue;
      }
      int next = skipJavaDelimiters(text, keyword.length());
      if (next >= text.length()) {
        return keyword;
      }
      return keyword + " " + text.substring(next).stripLeading();
    }
    return text;
  }

  private static boolean startsWithWord(String text, String word) {
    if (text == null || word == null || word.isEmpty()) {
      return false;
    }
    if (!text.startsWith(word)) {
      return false;
    }
    if (text.length() == word.length()) {
      return true;
    }
    char next = text.charAt(word.length());
    return !Character.isLetterOrDigit(next) && next != '_';
  }

  private static int skipJavaDelimiters(String source, int from) {
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
}
