package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class DoublePrefixNumberParser extends AbstractNumberParser{
  
  public DoublePrefixNumberParser() {
    super();
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes._double;
  }

  @Override
  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  @Override
  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.of(Parser.get(DoubleCastParser.class));
  }
}