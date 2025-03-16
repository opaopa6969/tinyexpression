package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface NumberExpression extends ExpressionInterface{

  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes.number;
  }

}

