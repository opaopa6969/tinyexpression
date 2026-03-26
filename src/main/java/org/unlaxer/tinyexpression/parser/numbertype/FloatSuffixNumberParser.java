package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.IgnoreCaseWordParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class FloatSuffixNumberParser extends NumberParser{
  
  public FloatSuffixNumberParser() {
    super();
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes._float;
  }

  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.of(
        new IgnoreCaseWordParser("f")
    );
  }

  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.empty();
  }

}