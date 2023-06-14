package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class StringSideEffectExpressionParser extends SideEffectExpressionParser{

  @Override
  Parser typedReturningParser() {
    return Parser.get(ReturningStringParser.class);
  }
}