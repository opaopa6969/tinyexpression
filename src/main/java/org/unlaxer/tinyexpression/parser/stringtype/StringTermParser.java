package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.tinyexpression.parser.AbstractStringTermParser;

public class StringTermParser extends AbstractStringTermParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}