package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class BigDecimalPrefixNumberParser extends AbstractNumberParser{
  
  public BigDecimalPrefixNumberParser() {
    super();
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes._bigDecimal;
  }

  @Override
  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  @Override
  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.of(Parser.get(BigDecimalCastParser.class));
  }
}