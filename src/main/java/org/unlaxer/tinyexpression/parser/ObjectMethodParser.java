package org.unlaxer.tinyexpression.parser;

public class ObjectMethodParser extends AbstractMethodParser {

  @Override
  public Class<? extends TypeHint> returningParser() {
    return ObjectTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return ObjectExpressionParser.class;
  }
}
