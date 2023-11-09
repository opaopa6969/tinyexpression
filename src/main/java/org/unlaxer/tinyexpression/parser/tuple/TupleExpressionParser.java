package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class TupleExpressionParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ImmediatelyTupleCreationParser.class),
        Parser.get(TupleVariableParser.class)
    );
  }
  
}