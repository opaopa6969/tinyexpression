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
  private final ExpressionType expressionType;

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
    expressionType = ExpressionTypes.of(resulTypeClass);
  }
  
  public ExpressionType expressionType(){
    return expressionType;
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
    return expressionType.isBoolean();
  }

  @Override
  public boolean isShort() {
    return expressionType.isShort();
  }

  @Override
  public boolean isByte() {
    return expressionType.isByte();
  }

  @Override
  public boolean isInt() {
    return expressionType.isInt();
  }

  @Override
  public boolean isFloat() {
    return expressionType.isFloat();
  }

  @Override
  public boolean isLong() {
    return expressionType.isLong();
  }

  @Override
  public boolean isDouble() {
    return expressionType.isDouble();
  }

  @Override
  public boolean isBigInteger() {
    return expressionType.isBigInteger();
  }

  @Override
  public boolean isBigDecimal() {
    return expressionType.isBigDecimal();
  }

  @Override
  public boolean isNumber() {
    return expressionType.isNumber();
  }

  @Override
  public boolean isBigNumber() {
    return expressionType.isBigNumber();
  }

  @Override
  public boolean isPrimitiveNumber() {
    return expressionType.isPrimitiveNumber();
  }

  @Override
  public boolean isVoid() {
    return expressionType.isVoid();
  }

  @Override
  public boolean isObject() {
    return expressionType.isObject();
  }

  @Override
  public boolean isString() {
    return expressionType.isString();
  }

  @Override
  public Class<?> javaType() {
    return resulTypeClass;
  }
  
  @Override
  public String javaTypeAsString() {
    return resulTypeClass.getTypeName();
  }


  @Override
  public boolean isTimestamp() {
    return expressionType.isTimestamp();
  }

  @Override
  public Optional<Class<?>> javaTypePrimitive() {
    return expressionType.javaTypePrimitive();
  }

  @Override
  public String javaLiteralSuffix() {
  	return expressionType.javaLiteralSuffix();
  }

  @Override
  public boolean isExternalJavaType() {
    return expressionType.isExternalJavaType();
  }
}