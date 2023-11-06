package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.bool.BooleanTypeHintSuffixParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ReturningBooleanParser extends JavaStyleDelimitedLazyChain implements Returning{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            Parser.get(()->new WordParser("returning"))
        ),
        Parser.get(BooleanTypeHintSuffixParser.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return boolean.class;
  }
}