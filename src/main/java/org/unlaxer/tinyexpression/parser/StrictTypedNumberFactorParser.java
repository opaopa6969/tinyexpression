package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;

public class StrictTypedNumberFactorParser extends AbstractNumberFactorParser{


  public StrictTypedNumberFactorParser(SpecifiedExpressionTypes specifiedExpressionType) {
    super(specifiedExpressionType);
    addTag(StrictTyped.get());
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(false);
  }

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }
}