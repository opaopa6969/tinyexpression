package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.tinyexpression.parser.AbstractBooleanExpressionParser;

public class BooleanExpressionParser extends AbstractBooleanExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}