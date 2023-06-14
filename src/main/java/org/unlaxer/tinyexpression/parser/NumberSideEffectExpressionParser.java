package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Optional;

public class NumberSideEffectExpressionParser extends SideEffectExpressionParser{

  @Override
  Parser typedReturningParser() {
    return new Optional(Parser.get(ReturningNumberParser.class));
  }
}