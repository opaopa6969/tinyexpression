package org.unlaxer.tinyexpression.parser;

public interface StringExpression extends ExpressionInterface{
  
  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes.string;
  }
}