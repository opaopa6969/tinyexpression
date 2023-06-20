package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class BooleanIfExpressionParser extends IfExpressionParser implements NumberExpression{

  @Override
  public Class<? extends Parser> strictTypedReturning() {
    return StrictTypedBooleanExpressionParser.class;
  }

  @Override
  public Class<? extends Parser> nonStrictTypedReturning() {
    return BooleanExpressionParser.class;
  }
}