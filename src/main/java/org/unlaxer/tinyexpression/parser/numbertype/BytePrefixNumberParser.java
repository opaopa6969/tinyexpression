package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.ConcreteNumberType;

public class BytePrefixNumberParser extends NumberParser implements ConcreteNumberType{

  public BytePrefixNumberParser() {
    super();
  }

  public ExpressionTypes expressionType() {
    return ExpressionTypes._byte;
  }

  java.util.Optional<Parser> typeSuffix() {
    return java.util.Optional.empty();
  }

  java.util.Optional<Parser> typePrefix() {
    return java.util.Optional.of(Parser.get(ByteCastParser.class));
  }
}