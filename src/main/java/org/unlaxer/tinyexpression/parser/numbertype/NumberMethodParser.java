package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

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