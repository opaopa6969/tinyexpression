package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface ListExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType() {
    return ExpressionType.list;
  }
}