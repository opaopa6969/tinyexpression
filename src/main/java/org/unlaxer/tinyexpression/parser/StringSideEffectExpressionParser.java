package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class StringSideEffectExpressionParser extends SideEffectExpressionParser implements StringExpression{

  @Override
  Parser typedReturningParser() {
    return Parser.get(ReturningStringParser.class);
  }
}