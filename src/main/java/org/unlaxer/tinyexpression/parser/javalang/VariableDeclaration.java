package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public interface VariableDeclaration extends Parser{
  
  public Optional<ExpressionTypes> type(); 
}