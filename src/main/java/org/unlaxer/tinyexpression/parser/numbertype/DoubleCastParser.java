package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class DoubleCastParser extends TypeCastParser{
  public DoubleCastParser() {
    super(ExpressionTypes._double);
  }
}