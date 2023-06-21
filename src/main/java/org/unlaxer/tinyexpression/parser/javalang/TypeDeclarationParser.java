package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.BooleanTypeHintParser;
import org.unlaxer.tinyexpression.parser.NumberTypeHintParser;
import org.unlaxer.tinyexpression.parser.StringTypeHintParser;

public class TypeDeclarationParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new WordParser("as"),
        new Choice(
            Parser.get(NumberTypeHintParser.class),
            Parser.get(BooleanTypeHintParser.class),
            Parser.get(StringTypeHintParser.class)
        )
    );
  }
}