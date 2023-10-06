package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ReturningStringParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class StringSideEffectExpressionParser extends SideEffectExpressionParser implements StringExpression{

  @Override
  public Parser typedReturningParser() {
    return Parser.get(ReturningStringParser.class);
  }
}