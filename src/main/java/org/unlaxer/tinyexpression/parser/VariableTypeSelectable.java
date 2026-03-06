package org.unlaxer.tinyexpression.parser;

public interface VariableTypeSelectable {
  
  public boolean hasNakedVariableParser();
  
  public org.unlaxer.parser.Parsers getLazyParsers(boolean withNakedVariable);
  
}