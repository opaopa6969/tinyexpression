package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.bool.BooleanVariableParser;
import org.unlaxer.tinyexpression.parser.number.NumberVariableParser;
import org.unlaxer.tinyexpression.parser.string.StringVariableParser;

public interface TypedVariableParser extends VariableParser{
  
  public default ExpressionType type(){
    return typeAsOptional().get();
  }
  
  public default RootVariableParser getRootVariableParer() {
    //FIXME!
    switch (type()) {
    case string:
      return Parser.get(StringVariableParser.class);
    case bool:
      return Parser.get(BooleanVariableParser.class);
    case number:
      return Parser.get(NumberVariableParser.class);
    case list:
      break;
    case map:
      break;
    case tuple:
      break;
    default:
      break;
    }
    throw new IllegalArgumentException(type().toString());
  }
  
}