package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class StringTypeHintParser extends LazyChoice implements TypeHint{

  private static final long serialVersionUID = 1652285131946632894L;

  public StringTypeHintParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
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