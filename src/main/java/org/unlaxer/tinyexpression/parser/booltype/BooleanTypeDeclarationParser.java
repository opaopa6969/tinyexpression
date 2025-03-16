package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class BooleanTypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            new WordParser("as")
        ),
        Parser.get(BooleanTypeHintParser.class)
    );
  }
}