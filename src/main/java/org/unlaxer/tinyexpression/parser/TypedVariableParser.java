package org.unlaxer.tinyexpression.parser;

public interface TypedVariableParser extends VariableParser{
  
  public default ExpressionType type(){
    return typeAsOptional().get();
  }
  
}