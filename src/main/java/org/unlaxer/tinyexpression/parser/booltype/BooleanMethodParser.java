package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.tinyexpression.parser.AbstractMethodParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class BooleanMethodParser extends AbstractMethodParser{
  
  @Override
  public Class<? extends TypeHint> returningParser() {
    return BooleanTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return BooleanExpressionParser.class;
  }
}