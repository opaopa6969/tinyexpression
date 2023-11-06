package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parsers;

public class StringTermParser extends AbstractStringTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}