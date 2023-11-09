package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class IfNotExistsParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("if"),
        new WordParser("not"),
        new WordParser("exists")
    );
  }
}