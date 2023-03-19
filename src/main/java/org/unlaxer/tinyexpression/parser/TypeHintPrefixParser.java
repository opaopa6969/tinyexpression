package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class TypeHintPrefixParser extends LazyChoice {

  private static final long serialVersionUID = 4114418513194666489L;

  List<Parser> parsers;

  public TypeHintPrefixParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(
        Parser.get(StringTypeHintPrefixParser.class),//
        Parser.get(NumberTypeHintPrefixParser.class),//
        Parser.get(BooleanTypeHintPrefixParser.class)//
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}