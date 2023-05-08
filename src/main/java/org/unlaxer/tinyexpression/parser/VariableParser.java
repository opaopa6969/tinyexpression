package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

public interface VariableParser{
  
  public Optional<VariableType> type();
  public default boolean hasType() {
    return type().isPresent();
  }
}