package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parsers;

public class NumberTermParser extends AbstractNumberTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public Parsers getLazyParsers(){
    return getLazyParsers(true);
  }
}