package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface NumberExpression extends ExpressionInterface{

  @Override
  public default ExpressionType expressionType() {
    return ExpressionTypes.number;
  }


}

