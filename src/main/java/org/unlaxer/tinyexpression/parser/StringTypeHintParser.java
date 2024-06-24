package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class StringTypeHintParser extends LazyChoice implements TypeHint{

  private static final long serialVersionUID = 1652285131946632894L;

  public StringTypeHintParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("String"), //
        new WordParser("string")
    );
  }

  @Override
  public ExpressionType type() {
    return ExpressionType.string;
  }
}