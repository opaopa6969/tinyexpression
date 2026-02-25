package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class TypeHintPrefixParser extends LazyChoice {

  private static final long serialVersionUID = 4114418513194666489L;

  public TypeHintPrefixParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return  new Parsers(
        Parser.get(ObjectTypeHintPrefixParser.class),//
        Parser.get(StringTypeHintPrefixParser.class),//
        Parser.get(NumberTypeHintPrefixParser.class),//
        Parser.get(BooleanTypeHintPrefixParser.class)//
    );
  }
}
