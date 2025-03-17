package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ReturningNumberParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class NumberSideEffectExpressionParser extends SideEffectExpressionParser implements NumberExpression{

  @Override
  public Parser typedReturningParser() {
    return new Optional(ReturningNumberParser.class);
  }

  @Override
  public ExpressionType expressionType(TypedToken<? extends ExpressionInterface> thisParserParsed) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'expressionType'");
  }
}