package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class FloatCastParser extends TypeCastParser{
  public FloatCastParser() {
    super(ExpressionTypes._float);
  }
}