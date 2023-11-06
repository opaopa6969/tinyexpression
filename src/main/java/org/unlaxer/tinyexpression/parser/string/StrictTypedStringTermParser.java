package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedStringTermParser extends AbstractStringTermParser{

  
  public StrictTypedStringTermParser() {
    super();
    addTag(StrictTyped.get());
  }

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(false);
  }
  
}