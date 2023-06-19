package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class NumberTermParser extends AbstractNumberTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public List<Parser> getLazyParsers(){
    return getLazyParsers(true);
  }
}