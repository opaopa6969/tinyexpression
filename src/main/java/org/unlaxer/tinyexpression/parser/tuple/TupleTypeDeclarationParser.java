package org.unlaxer.tinyexpression.parser.tuple;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class TupleTypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            new WordParser("as")
        ),
        Parser.get(TupleTypeHintParser.class)
    );
  }
}