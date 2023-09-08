package org.unlaxer.tinyexpression.parser;

public interface StringExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType() {
    return ExpressionType.string;
  }
}