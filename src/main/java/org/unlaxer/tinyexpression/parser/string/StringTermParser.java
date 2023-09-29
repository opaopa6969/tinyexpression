package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;

public class StringTermParser extends AbstractStringTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public List<Parser> getLazyParsers() {
    return getLazyParsers(true);
  }
  
}