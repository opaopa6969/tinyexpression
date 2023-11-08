package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class NumberTypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            new WordParser("as")
        ),
        Parser.get(NumberTypeHintParser.class)
    );
  }
}