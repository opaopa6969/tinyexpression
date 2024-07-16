package org.unlaxer.tinyexpression.parser;

public interface NumberExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType() {
    return ExpressionType.number;
  }

}

