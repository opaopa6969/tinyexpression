package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface BooleanExpression extends ExpressionInterface{

  default ExpressionType expressionType() {
    return ExpressionTypes._boolean;
  }
}