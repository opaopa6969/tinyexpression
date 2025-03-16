package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class NoSuffixNumberParser extends AbstractNumberParser{
  
  SpecifiedExpressionTypes specifiedExpressionType;
  

  public NoSuffixNumberParser(SpecifiedExpressionTypes specifiedExpressionType) {
    super();
    this.specifiedExpressionType = specifiedExpressionType;
  }

  @Override
  public ExpressionType expressionType() {
    return specifiedExpressionType.numberType();
  }

  @Override
  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  @Override
  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.empty();
  }
  
}