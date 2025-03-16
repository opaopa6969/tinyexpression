package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class ShortCastParser extends TypeCastParser{
  public ShortCastParser() {
    super(ExpressionTypes._short);
  }
}