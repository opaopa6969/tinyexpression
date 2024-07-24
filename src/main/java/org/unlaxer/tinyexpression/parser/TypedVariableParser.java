package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public interface TypedVariableParser extends VariableParser{
  
  public default ExpressionType type(){
    return typeAsOptional().get();
  }
  
  public default RootVariableParser getRootVariableParer() {
    
    switch (type()) {
    case string:
      return Parser.get(StringVariableParser.class);
    case _boolean:
      return Parser.get(BooleanVariableParser.class);
    case number:
      return Parser.get(NumberVariableParser.class);
      default:
        break;
    }
    throw new IllegalArgumentException(type().toString());
  }
  
}