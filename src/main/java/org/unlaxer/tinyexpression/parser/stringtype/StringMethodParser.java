package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.tinyexpression.parser.AbstractMethodParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class StringMethodParser extends AbstractMethodParser{

  @Override
  public Class<? extends TypeHint> returningParser() {
    return StringTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return StringExpressionParser.class;
  }
}