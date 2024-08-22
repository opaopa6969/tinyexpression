package org.unlaxer.tinyexpression.evaluator.javacode;

import java.math.BigDecimal;
import java.security.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.tinyexpression.parser.ExpressionType;

public class ResultType{
  public final String resulTypeAsString;
  public final Class<?> resulTypeClass;
  public ResultType(String resulTypeAsString) {
    super();
    this.resulTypeAsString = resulTypeAsString;
    
    classByName.computeIfAbsent(resulTypeAsString, name->{
      
      try {
        return Class.forName(resulTypeAsString);
      } catch (ClassNotFoundException e) {
        throw new RuntimeException(e);
      }
    });
    resulTypeClass = classByName.get(resulTypeAsString);
  }
  
  public Optional<ExpressionType> expressionType(){
    return ExpressionType.of(resulTypeClass);
  }
  
  static Map<String,Class<?>> classByName = new HashMap<>();
  
  static {

    classByName.put("float",Float.class);
    classByName.put("Float",Float.class);
    classByName.put("byte",Byte.class);
    classByName.put("Byte",Byte.class);
    classByName.put("short",Short.class);
    classByName.put("Short",Short.class);
    classByName.put("int",Integer.class);
    classByName.put("Integer",Integer.class);
    classByName.put("long",Long.class);
    classByName.put("Long",Long.class);
    classByName.put("double",Double.class);
    classByName.put("BigDecimal",BigDecimal.class);
    classByName.put("bigDecimal",BigDecimal.class);
        
    classByName.put("boolean",Boolean.class);
    classByName.put("Boolean",Boolean.class);
        
    classByName.put("string",String.class);
    classByName.put("String",String.class);
        
    classByName.put("timestamp",Timestamp.class);
    classByName.put("Timestamp",Timestamp.class);

    classByName.put("object",Object.class);
    classByName.put("Object",Object.class);
      
  }
}