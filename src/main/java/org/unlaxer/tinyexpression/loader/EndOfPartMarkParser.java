package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.loader.model.FormulaInfo;

/**
 * {@code ---END_OF_PART---} at column 0, whatever follows it on the line. Used as a value
 * terminator ({@link FormulaInfoElementTerminatorParser}) so that an entry's value never runs
 * over a line starting with the end mark (issue #211): such a line is either the end mark line
 * ({@link EndOfPartLineParser}) or a malformed one that makes the document fail, instead of
 * silently becoming a continuation of the value and merging the next block into this one.
 */
public class EndOfPartMarkParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StartOfLineParser.class),
        new WordParser(FormulaInfo.END_MARK)
    );
  }
}
