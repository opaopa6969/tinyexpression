package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;

public interface VariableTypeSelectable {
  
  public boolean hasNakedVariableParser();
  
  public List<Parser> getLazyParsers(boolean withNakedVariable);
  
}