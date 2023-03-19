package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class NumberTypeHintParser extends LazyChoice {

  private static final long serialVersionUID = 411285131946664894L;

  List<Parser> parsers;

  public NumberTypeHintParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(
        new WordParser("Number"), //
        new WordParser("number"), //
        new WordParser("Float"), //
        new WordParser("float")
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}