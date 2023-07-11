package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;

public interface VariableParser extends Parser{
  
  public Optional<ExpressionType> type();
  public default boolean hasType() {
    return type().isPresent();
  }
  
  public String getVariableName(Token thisParserParsed);
}