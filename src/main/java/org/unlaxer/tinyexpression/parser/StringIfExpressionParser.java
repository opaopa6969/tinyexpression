package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public class StringIfExpressionParser extends IfExpressionParser implements NumberExpression{

  @Override
  public Class<? extends Parser> strictTypedReturning() {
    return StrictTypedStringExpressionParser.class;
  }

  @Override
  public Class<? extends Parser> nonStrictTypedReturning() {
    return StringExpressionParser.class;
  }
}