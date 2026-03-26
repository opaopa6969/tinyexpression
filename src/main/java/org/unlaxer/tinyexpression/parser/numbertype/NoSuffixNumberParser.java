package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.evaluator.javacode.SpecifiedExpressionTypes;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class NoSuffixNumberParser extends NumberParser{
  
  SpecifiedExpressionTypes specifiedExpressionType;
  

  public NoSuffixNumberParser(SpecifiedExpressionTypes specifiedExpressionType) {
    super();
    this.specifiedExpressionType = specifiedExpressionType;
  }

  @Override
  public ExpressionType expressionType() {
    return specifiedExpressionType.numberType();
  }

  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.empty();
  }
  
}