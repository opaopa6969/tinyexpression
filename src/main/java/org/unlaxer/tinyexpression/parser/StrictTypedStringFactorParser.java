package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

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
  public List<Parser> getLazyParsers() {
    return getLazyParsers(false);
  }
}