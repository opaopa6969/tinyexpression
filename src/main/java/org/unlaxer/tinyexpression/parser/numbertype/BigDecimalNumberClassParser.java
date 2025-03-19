package org.unlaxer.tinyexpression.parser.numbertype;

import java.math.BigDecimal;
import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class BigDecimalNumberClassParser extends NumberClassParser {

  @Override
  public ExpressionType type() {
    return ExpressionTypes.bigDecimal;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("BigDecimal", false , ExpressionTypes.bigDecimal , BigDecimal.class)
    );
  }

  @Override
  public Class<?> returningType() {
   return BigDecimal.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return BigDecimalNumberClassParser.class;
  }
}