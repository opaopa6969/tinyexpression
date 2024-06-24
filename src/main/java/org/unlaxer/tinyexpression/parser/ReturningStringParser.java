package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ReturningStringParser extends JavaStyleDelimitedLazyChain implements Returning{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            Parser.get(()->new WordParser("returning"))
        ),
       Parser.get(StringTypeHintSuffixParser.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return String.class;
  }
}