package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class ObjectTypeHintParser extends LazyChoice implements TypeHint {

  private static final long serialVersionUID = 7933479417650402091L;

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("Object"),
        new WordParser("object")
    );
  }

  @Override
  public ExpressionTypes type() {
    return ExpressionTypes.object;
  }
}
