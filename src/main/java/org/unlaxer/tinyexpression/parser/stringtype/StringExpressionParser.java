package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.tinyexpression.parser.AbstractStringExpressionParser;

public class StringExpressionParser extends AbstractStringExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}