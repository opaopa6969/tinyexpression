package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;

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