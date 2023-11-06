package org.unlaxer.tinyexpression.parser.string;

import java.util.List;

import org.unlaxer.parser.Parser;

public class StringExpressionParser extends AbstractStringExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}