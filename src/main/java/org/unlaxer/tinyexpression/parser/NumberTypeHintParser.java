package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class NumberTypeHintParser extends LazyChoice {

  private static final long serialVersionUID = 411285131946664894L;

  public NumberTypeHintParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return
      new Parsers(
          new WordParser("Number"), //
          new WordParser("number"), //
          new WordParser("Float"), //
          new WordParser("float")
      );
  }
}