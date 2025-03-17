package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface StringExpression extends ExpressionInterface{
  
  default ExpressionType expressionType() {
    return ExpressionTypes.string;
  }
}