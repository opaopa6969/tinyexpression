package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class LongCastParser extends TypeCastParser{
  public LongCastParser() {
    super(ExpressionTypes._long);
  }
}