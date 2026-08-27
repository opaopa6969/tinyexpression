package org.unlaxer.tinyexpression.p4;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Source-span extraction for a mapped P4 AST node.
 *
 * <p>Historically this class also re-parsed slice syntax from the source text with a hand-written
 * char/bracket scanner ({@code parseSliceSnippet} and friends). That char-scanning shadow is gone:
 * slice indices are read directly from the {@code SliceExpr} AST now (grammar disambiguates
 * {@code [start:end:step]} into typed index rules — tinyexpression #35). All that remains is
 * {@link #sourceSnippetOfNode}, a reflection-based helper that returns the original substring a node
 * spans (used only as a defensive variable-name recovery fallback in the Java-code emitters).
 */
public final class P4SliceSourceSupport {

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
}
