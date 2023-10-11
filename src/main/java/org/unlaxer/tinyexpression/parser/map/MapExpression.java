package org.unlaxer.tinyexpression.parser.map;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface MapExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType() {
    return ExpressionType.map;
  }
}