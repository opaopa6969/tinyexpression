package org.unlaxer.tinyexpression.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fenced code blocks ({@code ```java:ClassName}) of a formula (issue #216 / #221), read
 * with the same line rules as the Rust {@code runtime::code_block} and the Java
 * {@code CodeStartParser} / {@code CodeEndParser}: a line that is exactly
 * {@code ```scheme:a.b.C} opens a block; the closing line has the same fence width.
 * Four or more backticks permit shorter fence lines inside the body. This legacy
 * text-inspection helper does not replace the committed parser's syntax validation.
 */
final class CodeBlocks {

  /** One block: its scheme ({@code java}), fully qualified class name and body. */
  static final class Block {
    final String scheme;
    final String className;
    final String body;

    Block(String scheme, String className, String body) {
      this.scheme = scheme;
      this.className = className;
      this.body = body;
    }
  }

  private CodeBlocks() {}

  /** Class names of every block (any scheme), in source order, without duplicates. */
  static List<String> classes(String source) {
    return new ArrayList<>(blocks(source).keySet());
  }

  /** The first block of each class name, in source order. An unclosed block runs to the end. */
  static Map<String, Block> blocks(String source) {
    Map<String, Block> blocks = new LinkedHashMap<>();
    if (source == null) {
      return blocks;
    }
    String[] open = null;
    String closingFence = null;
    StringBuilder body = null;
    for (Line original : lines(source)) {
      String line = original.text();
      if (open != null) {
        if (line.equals(closingFence)) {
          blocks.putIfAbsent(open[1], new Block(open[0], open[1], body.toString()));
          open = null;
        } else {
          body.append(line).append(original.ending());
        }
        continue;
      }
      String[] fence = openingFence(line);
      if (fence != null) {
        open = fence;
        closingFence = line.substring(0, line.indexOf(fence[0]));
        body = new StringBuilder();
      }
    }
    if (open != null) {
      blocks.putIfAbsent(open[1], new Block(open[0], open[1], body.toString()));
    }
    return blocks;
  }

  private record Line(String text, String ending) {}

  /** Keep host-language text and line endings unchanged, including CR-only input. */
  private static List<Line> lines(String source) {
    List<Line> lines = new ArrayList<>();
    int from = 0;
    while (from < source.length()) {
      int end = from;
      while (end < source.length() && source.charAt(end) != '\r' && source.charAt(end) != '\n') end++;
      int next = end;
      if (next < source.length() && source.charAt(next) == '\r') next++;
      if (next < source.length() && source.charAt(next) == '\n') next++;
      lines.add(new Line(source.substring(from, end), source.substring(end, next)));
      from = next;
    }
    return lines;
  }

  private static boolean identifierStart(int codePoint) {
    return (codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z')
        || codePoint == '_' || codePoint == '$';
  }

  private static boolean identifierPart(int codePoint) {
    return identifierStart(codePoint) || (codePoint >= '0' && codePoint <= '9');
  }

  /** End (code point index) of the identifier starting at {@code from}, or -1. */
  private static int identifierEnd(int[] codePoints, int from) {
    if (from >= codePoints.length || !identifierStart(codePoints[from])) {
      return -1;
    }
    int end = from + 1;
    while (end < codePoints.length && identifierPart(codePoints[end])) {
      end++;
    }
    return end;
  }

  /** {@code [scheme, className]} of an opening fence line, or null. */
  private static String[] openingFence(String line) {
    if (!line.startsWith("```")) {
      return null;
    }
    int[] codePoints = line.codePoints().toArray();
    int width = 3;
    while (width < codePoints.length && codePoints[width] == '`') width++;
    int schemeEnd = identifierEnd(codePoints, width);
    if (schemeEnd < 0 || schemeEnd >= codePoints.length || codePoints[schemeEnd] != ':') {
      return null;
    }
    int classFrom = schemeEnd + 1;
    int end = identifierEnd(codePoints, classFrom);
    if (end < 0) {
      return null;
    }
    while (end < codePoints.length && codePoints[end] == '.') {
      end = identifierEnd(codePoints, end + 1);
      if (end < 0) {
        return null;
      }
    }
    if (end != codePoints.length) {
      return null;
    }
    return new String[] {
        new String(codePoints, width, schemeEnd - width),
        new String(codePoints, classFrom, end - classFrom)};
  }
}
