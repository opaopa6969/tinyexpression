package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.VariableType;

public interface VariableDeclaration extends Parser{
  
  public Optional<VariableType> type(); 
}