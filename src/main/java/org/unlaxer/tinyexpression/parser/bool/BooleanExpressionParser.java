package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;

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