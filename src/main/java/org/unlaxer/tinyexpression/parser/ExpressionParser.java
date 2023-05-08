package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public class ExpressionParser extends AbstractExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }

  @Override
  public List<Parser> getLazyParsers() {
    return getLazyParsers(true);
  }
  
}