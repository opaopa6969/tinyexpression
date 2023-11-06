package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedNumberFactorParser extends AbstractNumberFactorParser{

  
  public StrictTypedNumberFactorParser() {
    super();
    addTag(StrictTyped.get());
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(false);
  }
  
  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }
}