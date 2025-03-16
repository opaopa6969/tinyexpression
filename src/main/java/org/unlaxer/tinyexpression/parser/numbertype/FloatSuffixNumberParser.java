package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.IgnoreCaseWordParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class FloatSuffixNumberParser extends AbstractNumberParser{
  
  public FloatSuffixNumberParser() {
    super();
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes._float;
  }

  @Override
  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.of(
        new IgnoreCaseWordParser("f")
    );
  }
  
  @Override
  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.empty();
  }

}