package org.unlaxer.tinyexpression.evaluator.javacode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class JavaStringLiteralsTest {

  @Test
  public void quoteEscapesEverythingThatCanEndTheLiteral() {
    assertEquals("\"a\\\"b\"", JavaStringLiterals.quote("a\"b"));
    assertEquals("\"a\\\\b\"", JavaStringLiterals.quote("a\\b"));
    assertEquals("\"\\n\\r\\t\\b\\f\"", JavaStringLiterals.quote("\n\r\t\b\f"));
    assertEquals("\"\\u0000\\u001f\\u007f\"", JavaStringLiterals.quote("\u0000\u001f\u007f"));
    assertEquals("\"\\u00e9\\ud83d\\ude00\\u2028\"", JavaStringLiterals.quote("é😀 "));
    assertEquals("\"*/ //\"", JavaStringLiterals.quote("*/ //"));
    assertEquals("\"\"", JavaStringLiterals.quote(null));
  }

  @Test
  public void quotedBackslashNeverStartsAUnicodeEscape() {
    // javac translates backslash-u before lexing; the quoted form keeps it a plain backslash.
    assertEquals("\"\\\\u0022\"", JavaStringLiterals.quote("\\u0022"));
  }

  @Test
  public void decodeFollowsJavac() {
    assertEquals("a\nb", JavaStringLiterals.decode("a\\nb"));
    assertEquals("it's", JavaStringLiterals.decode("it\\'s"));
    assertEquals("say \"hi\"", JavaStringLiterals.decode("say \\\"hi\\\""));
    assertEquals("back\\slash", JavaStringLiterals.decode("back\\\\slash"));
    assertEquals("\b\t\f\r ", JavaStringLiterals.decode("\\b\\t\\f\\r\\s"));
    assertEquals("A", JavaStringLiterals.decode("\\u0041"));
    assertEquals("A", JavaStringLiterals.decode("\\uuu0041"));
    assertEquals("\"", JavaStringLiterals.decode("\\u0022"));
    assertEquals("A\u0007ÿ", JavaStringLiterals.decode("\\101\\7\\377"));
    // octal takes at most three digits from 0-3, two from 4-7
    assertEquals("\u00ff7", JavaStringLiterals.decode("\\3777"));
    assertEquals("'7", JavaStringLiterals.decode("\\477"));
    // an escaped backslash does not start a unicode escape
    assertEquals("\\u0041", JavaStringLiterals.decode("\\\\u0041"));
    // a backslash produced by a unicode escape still forms an escape sequence
    assertEquals("x\"y", JavaStringLiterals.decode("x\\u005c\\u0022y"));
    assertEquals("x\ny", JavaStringLiterals.decode("x\\u005cny"));
  }

  @Test
  public void decodeRejectsWhatJavacRejects() {
    assertThrows(IllegalArgumentException.class, () -> JavaStringLiterals.decode("bad\\q"));
    assertThrows(IllegalArgumentException.class, () -> JavaStringLiterals.decode("tail\\"));
    assertThrows(IllegalArgumentException.class, () -> JavaStringLiterals.decode("\\u00"));
    assertThrows(IllegalArgumentException.class, () -> JavaStringLiterals.decode("\\u00g1"));
  }

  @Test
  public void decodeThenQuoteRoundTrips() {
    String[] values = {"", "plain", "a\"b", "a\\b", "\n\u0000é😀", "\\u0022"};
    for (String value : values) {
      String quoted = JavaStringLiterals.quote(value);
      assertEquals(value, JavaStringLiterals.decode(quoted.substring(1, quoted.length() - 1)));
    }
  }
}
