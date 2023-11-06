package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanTypeHintSuffixParser;
import org.unlaxer.tinyexpression.parser.number.NumberTypeHintSuffixParser;
import org.unlaxer.tinyexpression.parser.string.StringTypeHintSuffixParser;

public class TypeHintSuffixParser extends LazyChoice {

  private static final long serialVersionUID = 844185131946664894L;

  public TypeHintSuffixParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StringTypeHintSuffixParser.class),//
        Parser.get(NumberTypeHintSuffixParser.class),//
        Parser.get(BooleanTypeHintSuffixParser.class)//
    );
  }
}