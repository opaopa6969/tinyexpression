package org.unlaxer.tinyexpression.parser;

public class StringMethodParser extends AbstractMethodParser{

  @Override
  public Class<? extends TypeHint> returningParser() {
    return StringTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return StringExpressionParser.class;
  }
}