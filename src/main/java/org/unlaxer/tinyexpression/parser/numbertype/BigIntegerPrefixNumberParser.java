package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class BigIntegerPrefixNumberParser extends NumberParser{
  
  public BigIntegerPrefixNumberParser() {
    super();
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes._bigInteger;
  }

  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.of(Parser.get(BigIntegerCastParser.class));
  }
}