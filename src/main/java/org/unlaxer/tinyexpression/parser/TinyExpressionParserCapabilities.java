package org.unlaxer.tinyexpression.parser;

public final class TinyExpressionParserCapabilities {

  private static final String IF_KEYWORD = "if";
  private static final String MATCH_KEYWORD = "match";
  private static final String[] METHOD_INVOCATION_HEADS = {"call", "external", "internal"};

  private TinyExpressionParserCapabilities() {}

  public static int skipJavaStyleDelimiters(String source, int from) {
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

  public static String trimLeadingJavaStyleDelimiters(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    int index = skipJavaStyleDelimiters(source, 0);
    if (index <= 0) {
      return source;
    }
    if (index >= source.length()) {
      return "";
    }
    return source.substring(index);
  }

  public static boolean containsCommentAfterLeadingJavaStyleDelimiters(String source) {
    String trimmed = trimLeadingJavaStyleDelimiters(source);
    if (trimmed.isEmpty()) {
      return false;
    }
    return trimmed.indexOf("/*") >= 0 || trimmed.indexOf("//") >= 0;
  }

  public static boolean startsWithWord(String source, String word) {
    if (source == null || word == null || word.isEmpty()) {
      return false;
    }
    String text = source.stripLeading();
    return matchesWordAt(text, 0, word);
  }

  public static boolean matchesWordAt(String source, int index, String word) {
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

  public static boolean hasHead(String source, String keyword, Character requiredNext) {
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
    int next = skipJavaStyleDelimiters(text, keyword.length());
    return next < text.length() && text.charAt(next) == requiredNext;
  }

  public static String normalizeStructuredHead(String source) {
    String text = trimLeadingJavaStyleDelimiters(source).stripLeading();
    if (text.isEmpty()) {
      return text;
    }
    if (hasHead(text, IF_KEYWORD, '(')) {
      int next = skipJavaStyleDelimiters(text, IF_KEYWORD.length());
      return IF_KEYWORD + text.substring(next);
    }
    if (hasHead(text, MATCH_KEYWORD, '{')) {
      int next = skipJavaStyleDelimiters(text, MATCH_KEYWORD.length());
      return MATCH_KEYWORD + text.substring(next);
    }
    for (String keyword : METHOD_INVOCATION_HEADS) {
      if (!startsWithWord(text, keyword)) {
        continue;
      }
      int next = skipJavaStyleDelimiters(text, keyword.length());
      if (next >= text.length()) {
        return keyword;
      }
      return keyword + " " + text.substring(next).stripLeading();
    }
    return text;
  }

  public static String stripJavaStyleCommentsPreservingLayout(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder(source.length());
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < source.length(); i++) {
      char current = source.charAt(i);
      char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
      if (current == '\'' && !inDoubleQuote && prev != '\\') {
        inSingleQuote = !inSingleQuote;
        builder.append(current);
        continue;
      }
      if (current == '"' && !inSingleQuote && prev != '\\') {
        inDoubleQuote = !inDoubleQuote;
        builder.append(current);
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote && current == '/' && next == '/') {
        builder.append(' ').append(' ');
        i += 2;
        while (i < source.length() && source.charAt(i) != '\n') {
          builder.append(source.charAt(i) == '\r' ? '\r' : ' ');
          i++;
        }
        if (i < source.length()) {
          builder.append(source.charAt(i));
        }
        continue;
      }
      if (!inSingleQuote && !inDoubleQuote && current == '/' && next == '*') {
        builder.append(' ').append(' ');
        i += 2;
        while (i < source.length()) {
          char inner = source.charAt(i);
          char innerNext = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
          if (inner == '*' && innerNext == '/') {
            builder.append(' ').append(' ');
            i++;
            break;
          }
          builder.append(inner == '\n' || inner == '\r' ? inner : ' ');
          i++;
        }
        continue;
      }
      builder.append(current);
    }
    return builder.toString();
  }
}
