package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class IntNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes._int;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("int", true , ExpressionTypes._int , int.class),
        new NumberClassName("Integer", false , ExpressionTypes._int , Integer.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return int.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
   return IntNumberClassParser.class;
  }
}