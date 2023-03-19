package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class TypeHintSuffixParser extends LazyChoice {

  private static final long serialVersionUID = 844185131946664894L;

  List<Parser> parsers;

  public TypeHintSuffixParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(
        Parser.get(StringTypeHintSuffixParser.class),//
        Parser.get(NumberTypeHintSuffixParser.class),//
        Parser.get(BooleanTypeHintSuffixParser.class)//
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}