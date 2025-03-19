package org.unlaxer.tinyexpression.parser.numbertype;

public class StrictTypedNumberTermParser extends AbstractNumberTermParser{

  public StrictTypedNumberTermParser() {
    super();
    addTag(StrictTyped.get());
  }

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(){
    return getLazyParsers(false);
  }
}