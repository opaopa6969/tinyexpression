package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class IntCastParser extends TypeCastParser{
  public IntCastParser() {
    super(ExpressionTypes._int);
  }
}