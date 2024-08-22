package org.unlaxer.tinyexpression.evaluator.javacode;

import java.math.BigDecimal;
import java.security.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;

public class ResultType implements ExpressionType{
  private final String resulTypeAsString;
  private final Class<?> resulTypeClass;
  private final Tag tag;

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
    tag = Tag.of(resulTypeClass);
  }
  
  public Optional<ExpressionTypes> expressionType(){
    return ExpressionTypes.of(resulTypeClass);
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

  @Override
  public Tag asTag() {
    return tag;
  }

  @Override
  public boolean isBoolean() {
    return false;
  }

  @Override
  public boolean isShort() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isByte() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isInt() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isFloat() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isLong() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isDouble() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isBigInteger() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isBigDecimal() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isNumber() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isBigNumber() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isPrimitiveNumber() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isVoid() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isObject() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public boolean isString() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Class<?> javaType() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public boolean isTimestamp() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Optional<Class<?>> javaTypePrimitive() {
    // TODO Auto-generated method stub
    return Optional.empty();
  }
}