package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public interface VariableDeclaration extends Parser{
  
  public Optional<ExpressionType> type(TypedToken<? extends VariableDeclaration> thisParserParsed); 
}