package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.number.NumberVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.string.StringVariableMethodParameterParser;

public class MethodParameterParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(NumberVariableMethodParameterParser.class),
        Parser.get(StringVariableMethodParameterParser.class),
        Parser.get(BooleanVariableMethodParameterParser.class)
    );
  }
}