package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.parser.Parser;

public interface VariableDeclaration extends Parser{
  
  public Optional<ExpressionType> type(); 
}