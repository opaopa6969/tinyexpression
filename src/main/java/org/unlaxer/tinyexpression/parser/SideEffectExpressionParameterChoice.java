package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class SideEffectExpressionParameterChoice extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(StrictTypedBooleanClauseParser.class),
        Parser.get(StrictTypedStringExpressionParser.class),
        Parser.get(StrictTypedExpressionParser.class),
        Parser.get(BooleanClauseParser.class),
        Parser.get(StringExpressionParser.class),
        Parser.get(NumberExpressionParser.class)
    );
  }
  
}