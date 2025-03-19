package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class NumberNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes._float;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("number", true, ExpressionTypes._float , float.class),
        new NumberClassName("Number", false, ExpressionTypes._float , Float.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return float.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return NumberNumberClassParser.class;
  }
}