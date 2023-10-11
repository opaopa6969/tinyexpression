package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class ListTypeHintParser extends ListParser implements TypeHint{

  @Override
  public ExpressionType type() {
    return ExpressionType.list;
  }
}

