package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class ReturningParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return
        new Parsers(
          Parser.get(()->new WordParser("returning")),
          Parser.get(TypeHintSuffixParser.class)
        );
  }
  
}