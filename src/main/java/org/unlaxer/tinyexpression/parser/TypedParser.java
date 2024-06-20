package org.unlaxer.tinyexpression.parser;

import java.util.Set;

import org.unlaxer.parser.Parser;

public interface TypedParser extends Parser{
  
    public default <T extends Parser> T cast(Class<T> parserClass) {
      
      return parserClass.cast(this);
    }
    ExpressionType type();
    Set<TypedParser> allTypes();
    public default TypedParser typed(ExpressionType expressionType) {
      
      for(TypedParser typedParser : allTypes()) {
        
        if(typedParser.type() == expressionType) {
          return typedParser;
        }
      }
      throw new IllegalArgumentException(expressionType.name());
    }
    Set<TypedParser> otherTypes();
}