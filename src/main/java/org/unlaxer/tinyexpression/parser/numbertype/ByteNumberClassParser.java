package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class ByteNumberClassParser extends NumberClassParser {


  public ByteNumberClassParser() {
    super();
  }

  @Override
  public ExpressionType type() {
    return ExpressionTypes._byte;
  }

  @Override
  public List<NumberClassName> numberClassNames() {
    return List.of(
        new NumberClassName("byte", true , ExpressionTypes._byte , byte.class),
        new NumberClassName("Byte", false , ExpressionTypes._byte , Byte.class)
    );
  }

  @Override
  public Class<?> returningType() {
    return byte.class;
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return ByteNumberClassParser.class;
  }

}