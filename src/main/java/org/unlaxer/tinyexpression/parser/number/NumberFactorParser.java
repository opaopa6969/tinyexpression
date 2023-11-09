package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parsers;

public class NumberFactorParser extends AbstractNumberFactorParser{

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
}