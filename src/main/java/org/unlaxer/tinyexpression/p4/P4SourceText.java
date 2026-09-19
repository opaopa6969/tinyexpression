package org.unlaxer.tinyexpression.p4;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Owns the actual parser input and a position snapshot, never the mapper's latest global map. */
public final class P4SourceText {
  private static final P4SourceText LEXICAL = new P4SourceText(null, node -> Optional.empty());
  private final String parserSource;
  private final Function<Object, Optional<int[]>> spans;

  private P4SourceText(String parserSource, Function<Object, Optional<int[]>> spans) {
    this.parserSource = parserSource;
    this.spans = spans;
  }

  /** Compatibility for generated APIs whose slice fields still contain source strings. */
  public static P4SourceText lexicalOnly() {
    return LEXICAL;
  }

  /** The lookup must refer to an immutable, identity-based snapshot, not a live mapper. */
  public static P4SourceText fromSnapshot(String parserSource, Function<Object, Optional<int[]>> spans) {
    return new P4SourceText(Objects.requireNonNull(parserSource, "parserSource"),
        Objects.requireNonNull(spans, "spans"));
  }

  /** Adds an identity-mapped synthetic root spanning the complete owned parser input. */
  public P4SourceText withWholeSourceNode(Object node) {
    Objects.requireNonNull(node, "node");
    if (parserSource == null) return this;
    int end = parserSource.codePointCount(0, parserSource.length());
    return new P4SourceText(parserSource,
        value -> value == node ? Optional.of(new int[] {0, end}) : spans.apply(value));
  }

  Optional<int[]> ownedSpan(Object node) {
    return spans.apply(node).map(value -> value.clone());
  }

  /**
   * Combines a reparsed subtree snapshot with this document snapshot. Both snapshots use the same
   * code-point coordinate space; {@code overlay} may own a whitespace-masked copy of the source.
   */
  P4SourceText withOverlay(
      P4SourceText overlay,
      int overlayOffset,
      Object newOuter,
      Object oldOuter,
      Object newExpression,
      Object oldExpression) {
    Objects.requireNonNull(overlay, "overlay");
    if (parserSource == null) return this;
    return new P4SourceText(parserSource, value -> {
      if (value == newOuter) return spans.apply(oldOuter);
      if (value == newExpression) return spans.apply(oldExpression);
      Optional<int[]> overlaid = overlay.spans.apply(value);
      if (overlaid.isPresent()) {
        int[] range = overlaid.orElseThrow().clone();
        range[0] += overlayOffset;
        range[1] += overlayOffset;
        return Optional.of(range);
      }
      return spans.apply(value);
    });
  }

  /** Returns lexical input, without evaluating a node or using its debug representation. */
  public String text(Object value) {
    if (value == null) return null;
    if (value instanceof Optional<?> optional) return text(optional.orElse(null));
    if (value instanceof String text) return text;
    int[] span = spans.apply(value).orElseThrow(() -> new IllegalArgumentException(
        "No owned source span for " + value.getClass().getSimpleName()));
    if (parserSource == null || span.length != 2 || span[0] < 0 || span[1] < span[0]
        || span[1] > parserSource.codePointCount(0, parserSource.length())) {
      throw new IllegalArgumentException("Invalid owned source span");
    }
    return parserSource.substring(parserSource.offsetByCodePoints(0, span[0]),
        parserSource.offsetByCodePoints(0, span[1]));
  }
}
