package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parsers;

public class BooleanFactorParser extends AbstractBooleanFactorParser{

   @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
}