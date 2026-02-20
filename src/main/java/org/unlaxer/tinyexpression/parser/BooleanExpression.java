package org.unlaxer.tinyexpression.parser;

public interface BooleanExpression extends ExpressionInterface{

  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes._boolean;
  }
}