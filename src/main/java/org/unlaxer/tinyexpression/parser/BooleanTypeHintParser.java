package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class BooleanTypeHintParser extends LazyChoice {

  private static final long serialVersionUID = 212851319466314194L;

  List<Parser> parsers;

  public BooleanTypeHintParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(
        new WordParser("Boolean"), //
        new WordParser("boolean")
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}