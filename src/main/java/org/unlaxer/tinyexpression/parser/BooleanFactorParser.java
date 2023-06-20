package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class BooleanFactorParser extends AbstractBooleanFactorParser{

   @Override
  public List<Parser> getLazyParsers() {
    return getLazyParsers(true);
  }

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
}