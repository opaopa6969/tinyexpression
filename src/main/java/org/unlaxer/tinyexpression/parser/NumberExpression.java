package org.unlaxer.tinyexpression.parser;

public interface NumberExpression extends ExpressionInterface{
  
  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes.number;
  }

}

