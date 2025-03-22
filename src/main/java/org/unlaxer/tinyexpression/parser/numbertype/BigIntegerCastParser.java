package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class BigIntegerCastParser extends TypeCastParser{
  public BigIntegerCastParser() {
    super(ExpressionTypes._bigInteger);
  }
}