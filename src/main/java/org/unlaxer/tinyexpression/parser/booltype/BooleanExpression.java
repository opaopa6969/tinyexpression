package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface BooleanExpression extends ExpressionInterface{

  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes._boolean;
  }
}