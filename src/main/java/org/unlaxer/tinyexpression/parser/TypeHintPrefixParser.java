package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanTypeHintPrefixParser;
import org.unlaxer.tinyexpression.parser.number.NumberTypeHintPrefixParser;
import org.unlaxer.tinyexpression.parser.string.StringTypeHintPrefixParser;

public class TypeHintPrefixParser extends LazyChoice {

  private static final long serialVersionUID = 4114418513194666489L;

  public TypeHintPrefixParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return  new Parsers(
        Parser.get(StringTypeHintPrefixParser.class),//
        Parser.get(NumberTypeHintPrefixParser.class),//
        Parser.get(BooleanTypeHintPrefixParser.class)//
    );
  }
}