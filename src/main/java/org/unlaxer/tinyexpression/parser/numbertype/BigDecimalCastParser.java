package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class BigDecimalCastParser extends TypeCastParser{
  public BigDecimalCastParser() {
    super(ExpressionTypes.bigDecimal);
  }
}