package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface StringExpression extends ExpressionInterface{
  
  @Override
  default ExpressionType expressionType(Token thisParserParsed) {
    return ExpressionTypes.string;
  }
}