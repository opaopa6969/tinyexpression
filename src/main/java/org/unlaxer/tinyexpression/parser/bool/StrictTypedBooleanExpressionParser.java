package org.unlaxer.tinyexpression.parser.bool;

import java.util.List;

import org.unlaxer.parser.Parser;
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