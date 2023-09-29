package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface BooleanExpression extends ExpressionInterface{

  @Override
  default ExpressionType expressionType() {
    return ExpressionType.bool;
  }
}