package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class DoubleNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes._double;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("double", true , ExpressionTypes._double , double.class),
        new NumberClassName("Double", false , ExpressionTypes._double , Double.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return double.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return DoubleNumberClassParser.class;
  }
}