package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class StrictTypedNumberTermParser extends AbstractNumberTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }
  
  @Override
  public List<Parser> getLazyParsers(){
    return getLazyParsers(false);
  }
}