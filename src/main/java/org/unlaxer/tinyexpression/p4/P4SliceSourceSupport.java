package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class P4SliceSourceSupport {

  public record SliceParts(
      String valueSource,
      String startSource,
      String endSource,
      String stepSource) {}

  private P4SliceSourceSupport() {}

  public static Optional<String> sourceSnippetOfNode(Object node, String sourceFormula) {
    if (node == null || sourceFormula == null || sourceFormula.isEmpty()) {
      return Optional.empty();
    }
    try {
      String mapperClassName = node.getClass().getPackageName() + ".TinyExpressionP4Mapper";
      Class<?> mapperClass = Class.forName(mapperClassName, false, node.getClass().getClassLoader());
      Method sourceSpanOf = mapperClass.getMethod("sourceSpanOf", Object.class);
      Object spanObj = sourceSpanOf.invoke(null, node);
      if (!(spanObj instanceof Optional<?> spanOptional) || spanOptional.isEmpty()) {
        return Optional.empty();
      }
      Object span = spanOptional.get();
      if (!(span instanceof int[] positions) || positions.length < 2) {
        return Optional.empty();
      }
      int start = Math.max(0, Math.min(sourceFormula.length(), positions[0]));
      int end = Math.max(0, Math.min(sourceFormula.length(), positions[1]));
      if (end <= start) {
        return Optional.empty();
      }
      return Optional.of(sourceFormula.substring(start, end));
    } catch (Throwable ignored) {
      return Optional.empty();
    }
  }

  public static Optional<SliceParts> slicePartsOfNode(Object node, String sourceFormula) {
    return sourceSnippetOfNode(node, sourceFormula).flatMap(P4SliceSourceSupport::parseSliceSnippet);
  }

  public static Optional<SliceParts> parseSliceSnippet(String sliceSource) {
    if (sliceSource == null) {
      return Optional.empty();
    }
    String source = sliceSource.strip();
    if (source.isEmpty()) {
      return Optional.empty();
    }
    int openBracket = findTopLevelSliceOpenBracket(source);
    if (openBracket < 0) {
      return Optional.empty();
    }
    int closeBracket = findMatchingBracket(source, openBracket);
    if (closeBracket < 0 || closeBracket != source.length() - 1) {
      return Optional.empty();
    }
    String valueSource = source.substring(0, openBracket).strip();
    if (valueSource.isEmpty()) {
      return Optional.empty();
    }
    List<String> parts = splitTopLevel(source.substring(openBracket + 1, closeBracket), ':');
    if (parts.size() < 2 || parts.size() > 3) {
      return Optional.empty();
    }
    String startSource = normalizeComponent(parts.get(0));
    String endSource = normalizeComponent(parts.get(1));
    String stepSource = parts.size() == 3 ? normalizeComponent(parts.get(2)) : null;
    return Optional.of(new SliceParts(valueSource, startSource, endSource, stepSource));
  }

  private static String normalizeComponent(String component) {
    if (component == null) {
      return null;
    }
    String normalized = component.strip();
    return normalized.isEmpty() ? null : normalized;
  }

  private static int findTopLevelSliceOpenBracket(String source) {
    int parenDepth = 0;
    int braceDepth = 0;
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = 0; i < source.length(); i++) {
      char c = source.charAt(i);
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
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
            return i;
          }
          bracketDepth++;
        }
        case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
        default -> {
        }
      }
    }
    return -1;
  }

  private static int findMatchingBracket(String source, int openIndex) {
    int bracketDepth = 0;
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    for (int i = openIndex; i < source.length(); i++) {
      char c = source.charAt(i);
      char prev = i > 0 ? source.charAt(i - 1) : '\0';
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
      if (c == '[') {
        bracketDepth++;
      } else if (c == ']') {
        bracketDepth--;
        if (bracketDepth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static List<String> splitTopLevel(String text, char separator) {
    ArrayList<String> parts = new ArrayList<>();
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
        parts.add(text.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(text.substring(start));
    return parts;
  }
}
