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
  private final Optional<ExpressionTypes> expressionTypes;

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
    expressionTypes = ExpressionTypes.of(resulTypeClass);
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
    return expressionTypes.map(ExpressionType::isBoolean).orElse(false);
  }

  @Override
  public boolean isShort() {
    return expressionTypes.map(ExpressionType::isShort).orElse(false);
  }

  @Override
  public boolean isByte() {
    return expressionTypes.map(ExpressionType::isByte).orElse(false);
  }

  @Override
  public boolean isInt() {
    return expressionTypes.map(ExpressionType::isInt).orElse(false);
  }

  @Override
  public boolean isFloat() {
    return expressionTypes.map(ExpressionType::isFloat).orElse(false);
  }

  @Override
  public boolean isLong() {
    return expressionTypes.map(ExpressionType::isLong).orElse(false);
  }

  @Override
  public boolean isDouble() {
    return expressionTypes.map(ExpressionType::isDouble).orElse(false);
  }

  @Override
  public boolean isBigInteger() {
    return expressionTypes.map(ExpressionType::isBigInteger).orElse(false);
  }

  @Override
  public boolean isBigDecimal() {
    return expressionTypes.map(ExpressionType::isBigDecimal).orElse(false);
  }

  @Override
  public boolean isNumber() {
    return expressionTypes.map(ExpressionType::isNumber).orElse(false);
  }

  @Override
  public boolean isBigNumber() {
    return expressionTypes.map(ExpressionType::isBigNumber).orElse(false);
  }

  @Override
  public boolean isPrimitiveNumber() {
    return expressionTypes.map(ExpressionType::isPrimitiveNumber).orElse(false);
  }

  @Override
  public boolean isVoid() {
    return expressionTypes.map(ExpressionType::isVoid).orElse(false);
  }

  @Override
  public boolean isObject() {
    return expressionTypes.map(ExpressionType::isObject).orElse(false);
  }

  @Override
  public boolean isString() {
    return expressionTypes.map(ExpressionType::isString).orElse(false);
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
    return expressionTypes.map(ExpressionType::isTimestamp).orElse(false);
  }

  @Override
  public Optional<Class<?>> javaTypePrimitive() {
    return expressionTypes.flatMap(ExpressionType::javaTypePrimitive);
  }

@Override
public String javaLiteralSuffix() {
	return expressionTypes.map(ExpressionType::javaLiteralSuffix)
			.orElse(ExpressionType.super.javaLiteralSuffix());
}
  
  
}