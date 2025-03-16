package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.booltype.BooleanVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberVariableMethodParameterParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringVariableMethodParameterParser;

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