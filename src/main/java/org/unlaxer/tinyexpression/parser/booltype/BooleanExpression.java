package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface BooleanExpression extends ExpressionInterface{

  @Override
  default ExpressionType expressionType(Token thisParserParsed) {
    return ExpressionTypes._boolean;
  }
}