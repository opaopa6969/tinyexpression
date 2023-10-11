package org.unlaxer.tinyexpression.parser.map;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class MapTypeHintParser extends MapParser implements TypeHint{

  @Override
  public ExpressionType type() {
    return ExpressionType.map;
  }
}

