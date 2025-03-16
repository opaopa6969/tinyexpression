package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class StringTypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            new WordParser("as")
        ),
        Parser.get(StringTypeHintParser.class)
    );
  }
}