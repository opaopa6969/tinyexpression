package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class ByteNumberParser extends AbstractNumberParser{
  
  public ByteNumberParser() {
    super();
  }

  @Override
  public ExpressionType expressionType() {
    return ExpressionTypes._byte;
  }

  @Override
  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }
}