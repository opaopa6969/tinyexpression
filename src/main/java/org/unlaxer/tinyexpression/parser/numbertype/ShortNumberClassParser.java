package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class ShortNumberClassParser extends NumberClassParser{

  @Override
  public ExpressionType type() {
    return ExpressionTypes._short;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("short", true , ExpressionTypes._short, short.class),
        new NumberClassName("Short", false , ExpressionTypes._short , Short.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return short.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return ShortNumberClassParser.class;
  }
}