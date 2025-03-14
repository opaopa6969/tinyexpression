package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedNumberExpressionParser;

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