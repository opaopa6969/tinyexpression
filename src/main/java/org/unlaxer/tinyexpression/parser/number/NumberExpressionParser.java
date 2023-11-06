package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;

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