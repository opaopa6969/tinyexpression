package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;

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