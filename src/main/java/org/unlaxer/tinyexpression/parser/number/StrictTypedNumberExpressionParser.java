package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedNumberExpressionParser extends AbstractNumberExpressionParser{
  
  
  public StrictTypedNumberExpressionParser() {
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