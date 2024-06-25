package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.EndOfLineParser;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.elementary.WordParser;

public class EndOfPartLineParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StartOfLineParser.class),
        new WordParser("---END_OF_PART---"),
        Parser.get(EndOfLineParser.class)
    );
  }
}