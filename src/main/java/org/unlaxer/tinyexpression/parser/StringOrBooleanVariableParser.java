package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

public interface StringOrBooleanVariableParser extends VariableParser {
  public Optional<ExpressionType> typeAsOptional();

  public default boolean hasType() {
    return typeAsOptional().isPresent();
  }

}