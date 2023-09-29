package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;
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
  public List<Parser> getLazyParsers(){
    return getLazyParsers(false);
  }
}