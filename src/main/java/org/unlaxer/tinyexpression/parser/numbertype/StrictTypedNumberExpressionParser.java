package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedNumberExpressionParser extends AbstractNumberExpressionParser{


  public StrictTypedNumberExpressionParser() {
    super();
    addTag(StrictTyped.get());
  }

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }

}