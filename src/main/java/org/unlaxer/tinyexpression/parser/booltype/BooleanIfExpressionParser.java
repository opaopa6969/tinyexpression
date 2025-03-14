package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;

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