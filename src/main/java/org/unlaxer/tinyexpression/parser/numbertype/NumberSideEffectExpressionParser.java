package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Optional;

public class NumberSideEffectExpressionParser extends SideEffectExpressionParser implements NumberExpression{

  @Override
  public Parser typedReturningParser() {
    return new Optional(ReturningNumberParser.class);
  }
}