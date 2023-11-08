package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.StrictTyped;

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
  public Parsers getLazyParsers(){
    return getLazyParsers(false);
  }
}