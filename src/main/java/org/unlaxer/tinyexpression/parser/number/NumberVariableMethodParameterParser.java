package org.unlaxer.tinyexpression.parser.number;

import java.util.List;
import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypedVariableParser;

public class NumberVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , NumberExpression{


  public NumberVariableMethodParameterParser() {
    super();
  }
  
  @Override
  public Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberPrefixedVariableParser.class), 
          Parser.get(NumberSuffixedVariableParser.class)
      );
  }
  
  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.number);
  }
  
}