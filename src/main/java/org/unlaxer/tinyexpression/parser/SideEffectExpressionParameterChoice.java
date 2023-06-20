package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class SideEffectExpressionParameterChoice extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.newInstance(StrictTypedBooleanExpressionParser.class),
        Parser.newInstance(StrictTypedStringExpressionParser.class),
        Parser.newInstance
        (StrictTypedNumberExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(StringExpressionParser.class),
        Parser.get(NumberExpressionParser.class)
    );
  }
  
}