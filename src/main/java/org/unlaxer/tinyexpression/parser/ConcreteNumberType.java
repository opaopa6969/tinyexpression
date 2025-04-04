package org.unlaxer.tinyexpression.parser;

public interface ConcreteNumberType extends ExpressionInterface{
  public default ExpressionType concreteExpressionType() {
    return expressionType();
  }
}