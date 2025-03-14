package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class BooleanTypeHintParser extends LazyChoice implements TypeHint{

  private static final long serialVersionUID = 212851319466314194L;

  public BooleanTypeHintParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(
          new WordParser("Boolean"), //
          new WordParser("boolean")
      );
  }

  @Override
  public ExpressionTypes type() {
    return ExpressionTypes._boolean;
  }
}