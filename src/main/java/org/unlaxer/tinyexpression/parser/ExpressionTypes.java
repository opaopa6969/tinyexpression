package org.unlaxer.tinyexpression.parser;

import java.sql.Timestamp;
import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.compiler.CompileError;

public enum ExpressionTypes implements ExpressionType{
  _byte(Byte.class,byte.class),
  _short(Short.class,short.class),
  _int(Integer.class,int.class),
  _float(Float.class,float.class,"f"),
  _double(Double.class,double.class,"d"),
  _long(Long.class,long.class,"L"),
  bigDecimal(java.math.BigDecimal.class),
  bigInteger(java.math.BigInteger.class),
  number(Float.class,float.class),
  string(String.class),
  _boolean(Boolean.class,boolean.class),
  object(Object.class),
  timestamp(Timestamp.class),
  _void(Void.class , void.class)
  ;
  final Tag tag;
  final Class<?> javaTypePrimitive;
  final Class<?> javaType;
  final String javaLiteralSuffix;
  
  private ExpressionTypes(Class<?> javaType  , Class<?> javaTypePrimitive , String javaLiteralSuffix) {
    this.tag = Tag.of(this);
    this.javaType = javaType;
    this.javaTypePrimitive = javaTypePrimitive;
    this.javaLiteralSuffix = javaLiteralSuffix;
  }
  private ExpressionTypes(Class<?> javaType  , Class<?> javaTypePrimitive) {
    this.tag = Tag.of(this);
    this.javaType = javaType;
    this.javaTypePrimitive = javaTypePrimitive;
    this.javaLiteralSuffix = "";
  }
  
  private ExpressionTypes(Class<?> javaType) {
    this.tag = Tag.of(this);
    this.javaType = javaType;
    this.javaTypePrimitive = null;
    this.javaLiteralSuffix = "";
  }
  
  public Tag asTag() {
    return tag;
  }
  
  public boolean isBoolean() {
    return this == _boolean;
  }
  public boolean isShort() {
    return this == _short;
  }
  public boolean isByte() {
    return this == _byte;
  }
  public boolean isInt() {
    return this == _int;
  }
  public boolean isFloat() {
    return this == _float;
  }
  public boolean isLong() {
    return this == _long;
  }
  public boolean isDouble() {
    return this == _double;
  }
  public boolean isBigInteger() {
    return this == bigInteger;
  }
  public boolean isBigDecimal() {
    return this == bigDecimal;
  }
  public boolean isNumber() {
    return this == number ||  isByte() || isShort() ||
        isInt() || isFloat() || isLong() || isDouble() || 
        isBigInteger() || isBigDecimal();
  }
  
  public boolean isBigNumber() {
    return isBigInteger() || isBigDecimal();
  }
  
  public boolean isPrimitiveNumber() {
    return   isByte() || isShort() ||
        isInt() || isFloat() || isLong() || isDouble();
  }
  public boolean isVoid(){
    return this == _void;
  }
  public boolean isObject(){
    return this == object;
  }
  public boolean isString() {
    return this == string;
  }
  public Class<?> javaType() {
    return javaType;
  }
  public boolean isTimestamp() {
    return this == timestamp;
  }
  
  public Optional<Class<?>> javaTypePrimitive() {
    return Optional.ofNullable(javaTypePrimitive);
  }
  
  public static ExpressionType of(Class<?> clazz){
    
    for(ExpressionTypes expressionType : values()) {
      
      if(expressionType.javaType() == clazz || expressionType.javaTypePrimitive().map(x-> x==clazz).orElse(false)) {
        return  expressionType;
      }
    }
    return new JavaExpressionType(clazz);
  }
  @Override

  public String javaLiteralSuffix() {
    return javaLiteralSuffix;
  }
  
  public static ExpressionType of(String typeString){
      
    for(ExpressionTypes expressionType : values()) {
      if(expressionType.javaType.toString().equals(typeString) || 
          (expressionType.javaTypePrimitive != null && expressionType.javaTypePrimitive.toString().equals(typeString))){
        return expressionType;
      }
    }
    try {
      return new JavaExpressionType(Class.forName(typeString));
    } catch (ClassNotFoundException e) {
      throw new CompileError(typeString+" not found.", e);
    }
  }
  
  @Override
  public boolean isExternalJavaType() {
    return isObject(); 
  }
}