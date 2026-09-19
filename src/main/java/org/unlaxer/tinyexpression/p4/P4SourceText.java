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
