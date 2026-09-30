package org.unlaxer.tinyexpression.evaluator.javacode;

/**
 * Conversion between tinyexpression string literal contents and Java string literals in
 * generated source.
 *
 * <p>The legacy javacode backends used to paste the contents of a formula's string literal
 * between two double quotes in the generated Java source. A {@code "} inside a single-quoted
 * literal therefore closed the Java literal and the rest of the formula text was compiled as
 * Java code. {@link #quote(String)} is the only way a literal value reaches generated source now.
 *
 * <p>Those backends also let javac interpret the backslash sequences in the literal
 * ({@code \n}, {@code \'}, {@code \\}, {@code A}, octal). {@link #decode(String)} keeps
 * that meaning: it turns the contents into the value javac produced, so every formula that
 * compiled before evaluates to the same string.
 */
public final class JavaStringLiterals {

  private JavaStringLiterals() {}

  /**
   * Returns a Java string literal (with the surrounding double quotes) whose value is
   * {@code value}. The result contains only printable ASCII, so neither javac's Unicode escape
   * translation nor the source encoding can change where the literal ends.
   */
  public static String quote(String value) {
    String text = value == null ? "" : value;
    StringBuilder builder = new StringBuilder(text.length() + 2);
    builder.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '\\' -> builder.append("\\\\");
        case '"' -> builder.append("\\\"");
        case '\n' -> builder.append("\\n");
        case '\r' -> builder.append("\\r");
        case '\t' -> builder.append("\\t");
        case '\b' -> builder.append("\\b");
        case '\f' -> builder.append("\\f");
        default -> {
          if (c < 0x20 || c > 0x7e) {
            builder.append(String.format("\\u%04x", (int) c));
          } else {
            builder.append(c);
          }
        }
      }
    }
    builder.append('"');
    return builder.toString();
  }

  /**
   * Returns the value javac gives to a string literal whose contents (between the quotes) are
   * {@code contents}: Unicode escapes first (JLS 3.3), then escape sequences (JLS 3.10.7).
   *
   * @throws IllegalArgumentException for an escape javac rejects
   */
  public static String decode(String contents) {
    return decodeEscapeSequences(translateUnicodeEscapes(contents == null ? "" : contents), contents);
  }

  private static String translateUnicodeEscapes(String text) {
    StringBuilder builder = new StringBuilder(text.length());
    int rawBackslashes = 0;
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      boolean eligible = c == '\\' && rawBackslashes % 2 == 0
          && i + 1 < text.length() && text.charAt(i + 1) == 'u';
      if (!eligible) {
        builder.append(c);
        rawBackslashes = c == '\\' ? rawBackslashes + 1 : 0;
        i++;
        continue;
      }
      int j = i + 1;
      while (j < text.length() && text.charAt(j) == 'u') {
        j++;
      }
      if (j + 4 > text.length()) {
        throw illegal("an illegal unicode escape", text);
      }
      int code = 0;
      for (int k = j; k < j + 4; k++) {
        int digit = Character.digit(text.charAt(k), 16);
        if (digit < 0) {
          throw illegal("an illegal unicode escape", text);
        }
        code = code * 16 + digit;
      }
      builder.append((char) code);
      // A backslash produced by an escape never starts another escape (JLS 3.3).
      rawBackslashes = 0;
      i = j + 4;
    }
    return builder.toString();
  }

  private static String decodeEscapeSequences(String text, String original) {
    StringBuilder builder = new StringBuilder(text.length());
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c != '\\') {
        builder.append(c);
        i++;
        continue;
      }
      if (i + 1 >= text.length()) {
        throw illegal("a dangling backslash", original);
      }
      char next = text.charAt(i + 1);
      switch (next) {
        case 'b' -> builder.append('\b');
        case 't' -> builder.append('\t');
        case 'n' -> builder.append('\n');
        case 'f' -> builder.append('\f');
        case 'r' -> builder.append('\r');
        case 's' -> builder.append(' ');
        case '"' -> builder.append('"');
        case '\'' -> builder.append('\'');
        case '\\' -> builder.append('\\');
        default -> {
          if (next < '0' || next > '7') {
            throw illegal("an illegal escape character '" + next + "'", original);
          }
          int maxDigits = next <= '3' ? 3 : 2;
          int j = i + 1;
          int code = 0;
          while (j < text.length() && j < i + 1 + maxDigits
              && text.charAt(j) >= '0' && text.charAt(j) <= '7') {
            code = code * 8 + (text.charAt(j) - '0');
            j++;
          }
          builder.append((char) code);
          i = j;
          continue;
        }
      }
      i += 2;
    }
    return builder.toString();
  }

  private static IllegalArgumentException illegal(String reason, String contents) {
    return new IllegalArgumentException(
        "string literal has " + reason + ": " + quote(contents));
  }
}
