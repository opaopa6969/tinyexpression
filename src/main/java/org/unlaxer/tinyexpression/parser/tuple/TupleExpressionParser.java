package org.unlaxer.tinyexpression.parser.tuple;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class TupleExpressionParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImmediatelyTupleCreationParser.class),
        Parser.get(TupleVariableParser.class)
    );
  }
  
}