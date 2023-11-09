package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parsers;

public class NumberExpressionParser extends AbstractNumberExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}