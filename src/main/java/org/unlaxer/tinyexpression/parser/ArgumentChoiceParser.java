package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ArgumentChoiceParser extends LazyChoice{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.newInstance(StrictTypedBooleanExpressionParser.class),
        Parser.newInstance(StrictTypedStringExpressionParser.class),
        Parser.newInstance
        (StrictTypedNumberExpressionParser.class),
        Parser.get(NumberExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(StringExpressionParser.class)
    );
  }
  
}