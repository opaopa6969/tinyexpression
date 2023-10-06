package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class TupleTypeHintParser extends TupleParser implements TypeHint{

  @Override
  public ExpressionType type() {
    return ExpressionType.tuple;
  }
}