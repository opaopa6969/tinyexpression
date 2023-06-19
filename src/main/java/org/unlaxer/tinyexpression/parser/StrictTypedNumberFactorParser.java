package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class StrictTypedNumberFactorParser extends AbstractFactorParser{

  @Override
  public List<Parser> getLazyParsers() {
    return getLazyParsers(false);
  }
  
  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }
}