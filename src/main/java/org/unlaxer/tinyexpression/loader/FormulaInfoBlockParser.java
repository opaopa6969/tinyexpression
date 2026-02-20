package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;

public class FormulaInfoBlockParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(FormulaInfoParser.class),
        Parser.get(EndOfPartParser.class)
    );
  }
}