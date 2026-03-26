package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class MethodParameterParser extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ObjectVariableMethodParameterParser.class),
        Parser.get(NumberVariableMethodParameterParser.class),
        Parser.get(StringVariableMethodParameterParser.class),
        Parser.get(BooleanVariableMethodParameterParser.class)
    );
  }
}
