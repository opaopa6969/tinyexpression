package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ReturningBooleanParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class BooleanSideEffectExpressionParser extends SideEffectExpressionParser{

  @Override
  Parser typedReturningParser() {
    return Parser.get(ReturningBooleanParser.class);
  }
}