package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.AbstractMethodParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class NumberMethodParser extends AbstractMethodParser{

  @Override
  public Class<? extends TypeHint> returningParser() {
    return NumberTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return NumberExpressionParser.class;
  }
}