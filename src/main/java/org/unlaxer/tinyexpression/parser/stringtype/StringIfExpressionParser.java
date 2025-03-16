package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.StrictTypedStringExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpression;

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