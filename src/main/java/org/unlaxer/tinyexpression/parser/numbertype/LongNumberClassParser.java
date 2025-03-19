package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class LongNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes._long;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("long", true , ExpressionTypes._long , long.class),
        new NumberClassName("Long", false , ExpressionTypes._long , Long.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return long.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return LongNumberClassParser.class;
  }
}