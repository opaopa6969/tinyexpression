package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class BooleanSideEffectExpressionParser extends SideEffectExpressionParser{

  @Override
  Parser typedReturningParser() {
    return Parser.get(ReturningBooleanParser.class);
  }
}