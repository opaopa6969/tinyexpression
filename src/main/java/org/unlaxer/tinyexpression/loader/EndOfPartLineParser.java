package org.unlaxer.tinyexpression.loader;

import java.util.List;
import java.util.stream.Collectors;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.EndOfLineParser;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.BlankParser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

/**
 * The end mark line: {@code ---END_OF_PART---} at column 0, optionally followed by spaces and
 * tabs, then a line break or the end of input.
 *
 * <p>Issue #211: trailing spaces/tabs used to make the line an ordinary value line (the value
 * of the entry before it continued over it), which silently merged the next block into this
 * one. They are now allowed. A line that starts with the end mark and continues with anything
 * else is rejected ({@link #rejectMalformedEndMarkLines(String)}), never read as a value line;
 * {@link EndOfPartMarkParser} stops an entry's value in front of such a line.
 */
public class EndOfPartLineParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StartOfLineParser.class),
        new WordParser(FormulaInfo.END_MARK),
        new ZeroOrMore(BlankParser.class),
        Parser.get(EndOfLineParser.class)
    );
  }

  /**
   * Rejects a document containing a line that starts with {@code ---END_OF_PART---} but is not
   * an end mark line (issue #211): after the mark only {@code ' '} and {@code '\t'} may follow
   * up to the line break or the end of input.
   *
   * <p>Such a line can never be parsed (it is neither an entry, a comment, a blank line, a value
   * line nor the end mark), so this check changes no accept/reject outcome; it only names the
   * offending line instead of the generic "only partially parsed" message.
   *
   * @throws FormulaInfoParseException naming the first such line (1-based)
   */
  public static void rejectMalformedEndMarkLines(String text) {
    // String.lines() splits at "\r\n", "\r" and "\n": the same line starts as StartOfLineParser.
    List<String> lines = text.lines().collect(Collectors.toList());
    for (int index = 0; index < lines.size(); index++) {
      String line = lines.get(index);
      if (false == line.startsWith(FormulaInfo.END_MARK)) {
        continue;
      }
      // END_MARK is ASCII, so its char length is also its code point length.
      String rest = line.substring(FormulaInfo.END_MARK.length());
      boolean onlyBlanks = rest.codePoints().allMatch(
          codePoint -> codePoint == ' ' || codePoint == '\t');
      if (false == onlyBlanks) {
        throw new FormulaInfoParseException(
            "line " + (index + 1) + ": '" + FormulaInfo.END_MARK
                + "' must be followed only by spaces or tabs up to the end of the line, but found '"
                + rest + "'");
      }
    }
  }
}
