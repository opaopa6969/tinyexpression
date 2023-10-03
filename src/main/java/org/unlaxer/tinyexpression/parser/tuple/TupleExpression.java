package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface TupleExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType() {
    return ExpressionType.tuple;
  }
}