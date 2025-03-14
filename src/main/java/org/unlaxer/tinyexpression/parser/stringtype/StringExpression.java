package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface StringExpression extends ExpressionInterface{
  
  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes.string;
  }
}