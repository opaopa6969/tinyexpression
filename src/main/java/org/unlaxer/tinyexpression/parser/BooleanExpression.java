package org.unlaxer.tinyexpression.parser;

public interface BooleanExpression extends ExpressionInterface{

  @Override
  default ExpressionType expressionType() {
    return ExpressionType.bool;
  }
}