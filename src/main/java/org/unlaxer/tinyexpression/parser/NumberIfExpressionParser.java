package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class NumberIfExpressionParser extends IfExpressionParser implements NumberExpression{

  @Override
  public Class<? extends Parser> strictTypedReturning() {
    return StrictTypedNumberExpressionParser.class;
  }

  @Override
  public Class<? extends Parser> nonStrictTypedReturning() {
    return NumberExpressionParser.class;
  }
}