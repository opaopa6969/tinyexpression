package org.unlaxer.tinyexpression.parser.numbertype;

import java.math.BigInteger;
import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class BigIntegerNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes.bigInteger;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("BigInteger", false , ExpressionTypes.bigInteger , BigInteger.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return BigInteger.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return BigIntegerNumberClassParser.class;
  }
}