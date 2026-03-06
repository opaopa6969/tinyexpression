package org.unlaxer.tinyexpression.parser;

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
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }
}