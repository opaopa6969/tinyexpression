package org.unlaxer.tinyexpression.parser;

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
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }

}