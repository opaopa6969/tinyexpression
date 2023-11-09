package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedStringFactorParser extends AbstractStringFactorParser{


  public StrictTypedStringFactorParser() {
    super();
    addTag(StrictTyped.get());
  }

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(false);
  }
}