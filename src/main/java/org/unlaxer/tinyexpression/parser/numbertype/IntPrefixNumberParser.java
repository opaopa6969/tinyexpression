package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class IntPrefixNumberParser extends NumberParser{
  
  public IntPrefixNumberParser() {
    super();
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes._int;
  }

  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.of(Parser.get(IntCastParser.class));
  }
}