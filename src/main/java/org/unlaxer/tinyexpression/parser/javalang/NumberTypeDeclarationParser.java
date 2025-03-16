package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberTypeHintParser;

public class NumberTypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            new WordParser("as")
        ),
        Parser.get(NumberTypeHintParser.class)
    );
  }
}