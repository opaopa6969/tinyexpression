package org.unlaxer.tinyexpression.parser;

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