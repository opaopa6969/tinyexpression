package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Tag;

public class JavaExpressionType implements ExpressionType{
  
  Class<?> clazz;
  Tag tag;
  

  public JavaExpressionType(Class<?> clazz) {
    super();
    this.clazz = clazz;
    tag = Tag.of(clazz);
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
    return false;
  }

  @Override
  public boolean isByte() {
    return false;
  }

  @Override
  public boolean isInt() {
    return false;
  }

  @Override
  public boolean isFloat() {
    return false;
  }

  @Override
  public boolean isLong() {
    return false;
  }

  @Override
  public boolean isDouble() {
    return false;
  }

  @Override
  public boolean isBigInteger() {
    return false;
  }

  @Override
  public boolean isBigDecimal() {
    return false;
  }

  @Override
  public boolean isNumber() {
    return false;
  }

  @Override
  public boolean isBigNumber() {
    return false;
  }

  @Override
  public boolean isPrimitiveNumber() {
    return false;
  }

  @Override
  public boolean isVoid() {
    return false;
  }

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public boolean isString() {
    return false;
  }

  @Override
  public boolean isTimestamp() {
    return false;
  }

  @Override
  public Class<?> javaType() {
    return clazz;
  }

  @Override
  public Optional<Class<?>> javaTypePrimitive() {
    return Optional.empty();
  }

  @Override
  public boolean isExternalJavaType() {
    return true;
  }
  
}