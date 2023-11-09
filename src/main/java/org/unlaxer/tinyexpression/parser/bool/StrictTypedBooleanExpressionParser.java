package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.StrictTyped;

public class StrictTypedBooleanExpressionParser extends AbstractBooleanExpressionParser{

  
  public StrictTypedBooleanExpressionParser() {
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