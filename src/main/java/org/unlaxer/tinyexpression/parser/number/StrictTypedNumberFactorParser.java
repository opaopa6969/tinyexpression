package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;
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