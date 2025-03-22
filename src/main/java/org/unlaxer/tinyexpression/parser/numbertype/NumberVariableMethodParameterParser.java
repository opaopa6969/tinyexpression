package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.TypedVariableParser;

@SuppressWarnings("serial")
public class NumberVariableMethodParameterParser extends LazyChoice implements TypedVariableParser , NumberExpression{


  public NumberVariableMethodParameterParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(//
          Parser.get(NumberPrefixedVariableParser.class),
          Parser.get(NumberSuffixedVariableParser.class)
      );
  }

//  @Override
//  public Optional<ExpressionType> typeAsOptional() {
//    return Optional.of(ExpressionTypes.number);
//  }

}