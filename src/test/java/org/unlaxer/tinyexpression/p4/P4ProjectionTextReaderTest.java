package org.unlaxer.tinyexpression.p4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import java.util.Optional;
import org.junit.Test;

public class P4ProjectionTextReaderTest {
  @Test
  public void readsForwardReverseRepeatedUnicodeAndEofSpans() {
    var source = P4SourceText.fromSnapshot("a😀e\u0301👨‍👩‍👧‍👦z",
        node -> Optional.of((int[]) node));
    var reader = source.projectionTextReader();
    assertEquals("😀", reader.text(new int[] {1, 2}));
    assertEquals("👨‍👩‍👧‍👦", reader.text(new int[] {4, 11}));
    assertEquals("a", reader.text(new int[] {0, 1}));
    assertEquals("e\u0301", reader.text(new int[] {2, 4}));
    assertEquals("😀", reader.text(new int[] {1, 2}));
    assertEquals("😀", reader.text(new int[] {1, 2}));
    assertEquals("", reader.text(new int[] {12, 12}));
    for (int[] invalid : new int[][] {{-1, 0}, {4, 3}, {12, 13}, {0}, {0, 1, 2}}) {
      assertThrows(IllegalArgumentException.class, () -> reader.text(invalid));
    }
    assertEquals("a", reader.text(new int[] {0, 1}));
  }
}
