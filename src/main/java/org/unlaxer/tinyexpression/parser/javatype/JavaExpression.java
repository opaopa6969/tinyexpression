package org.unlaxer.tinyexpression.parser.javatype;

import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface JavaExpression extends ExpressionInterface{

  @Override
  default ExpressionTypes expressionType() {
    return ExpressionTypes.object;
  }

}