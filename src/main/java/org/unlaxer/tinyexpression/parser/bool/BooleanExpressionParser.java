package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parsers;

public class BooleanExpressionParser extends AbstractBooleanExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}