package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class MethodParameterParser extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(NumberVariableMethodParameterParser.class),
        Parser.get(StringVariableMethodParameterParser.class),
        Parser.get(BooleanVariableMethodParameterParser.class)
    );
  }
}