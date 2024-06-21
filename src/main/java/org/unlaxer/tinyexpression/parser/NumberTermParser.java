package org.unlaxer.tinyexpression.parser;

public class NumberTermParser extends AbstractNumberTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers(){
    return getLazyParsers(true);
  }
}