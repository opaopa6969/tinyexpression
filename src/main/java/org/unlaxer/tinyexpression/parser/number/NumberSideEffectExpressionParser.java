package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.ReturningNumberParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class NumberSideEffectExpressionParser extends SideEffectExpressionParser{

  @Override
  Parser typedReturningParser() {
    return new Optional(Parser.get(ReturningNumberParser.class));
  }
}