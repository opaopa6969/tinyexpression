package org.unlaxer.tinyexpression.codeblock;

import java.util.function.IntUnaryOperator;

/** Pure, atomic recognition of an extended code block. All offsets are UTF-16.
 * The opening fence has at least four backticks. Only a line consisting of exactly
 * the same number closes it; host-language strings/comments are deliberately opaque.
 */
public final class LongCodeFence {
  private LongCodeFence() {}
  public record Layout(int width, int headerEnd, int bodyStart, int bodyEnd,
      int fenceEnd, int end) {}

  public static Layout scan(String source, int start) {
    return scan(source.length(), source::charAt, start);
  }

  public static Layout scan(int length, IntUnaryOperator at, int start) {
    if (start < 0 || start >= length || (start > 0 && !eol(at.applyAsInt(start - 1)))) return null;
    int p = start;
    while (p < length && at.applyAsInt(p) == '`') p++;
    int width = p - start;
    if (width < 4) return null;
    p = identifierEnd(length, at, p);
    if (p < 0 || p >= length || at.applyAsInt(p++) != ':') return null;
    p = identifierEnd(length, at, p);
    if (p < 0) return null;
    while (p < length && at.applyAsInt(p) == '.') {
      p = identifierEnd(length, at, p + 1);
      if (p < 0) return null;
    }
    if (p >= length || !eol(at.applyAsInt(p))) return null;
    int bodyStart = afterLine(length, at, p);
    int close = closingLine(length, at, bodyStart, width);
    if (close < 0) return null;
    int fenceEnd = close + width;
    return new Layout(width, p, bodyStart, close, fenceEnd, afterLine(length, at, fenceEnd));
  }

  /** Layout-preserving comment masking also keeps malformed/unclosed bodies opaque.
   * This does not accept them as syntax; only scan() does that.
   */
  public static int opaqueEnd(String source, int start) {
    int p = start;
    while (p < source.length() && source.charAt(p) == '`') p++;
    int close = closingLine(source.length(), source::charAt, p, p - start);
    return close < 0 ? source.length() : close + p - start;
  }

  private static int closingLine(int length, IntUnaryOperator at, int from, int width) {
    for (int p = from; p < length;) {
      if ((p == 0 || eol(at.applyAsInt(p - 1))) && at.applyAsInt(p) == '`') {
        int start = p;
        while (p < length && at.applyAsInt(p) == '`') p++;
        if (p - start == width && (p == length || eol(at.applyAsInt(p)))) return start;
      } else {
        p++;
      }
    }
    return -1;
  }

  private static int identifierEnd(int length, IntUnaryOperator at, int p) {
    if (p >= length || !head(at.applyAsInt(p))) return -1;
    while (++p < length && (head(at.applyAsInt(p)) || digit(at.applyAsInt(p)))) { }
    return p;
  }
  private static boolean head(int c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_'; }
  private static boolean digit(int c) { return c >= '0' && c <= '9'; }
  private static boolean eol(int c) { return c == '\r' || c == '\n'; }
  private static int afterLine(int length, IntUnaryOperator at, int p) {
    if (p < length && at.applyAsInt(p) == '\r') p++;
    if (p < length && at.applyAsInt(p) == '\n') p++;
    return p;
  }
}
