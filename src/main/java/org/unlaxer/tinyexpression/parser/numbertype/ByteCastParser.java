package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeCastParser;

public class ByteCastParser extends TypeCastParser{
  public ByteCastParser() {
    super(ExpressionTypes._byte);
  }
}