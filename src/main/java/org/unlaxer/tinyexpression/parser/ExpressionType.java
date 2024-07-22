package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public enum ExpressionType{
  number("Float","float"),
  string("String","String"),
  bool("Boolean","boolean"),
  object("Object","Object")
  ;
  final Tag tag;
  final String javaTypePrimitive;
  final String javaType;
  
  private ExpressionType(String javaType  , String javaTypePrimitive) {
    this.tag = Tag.of(this);
    this.javaType = javaType;
    this.javaTypePrimitive = javaTypePrimitive;
  }

  public Tag asTag() {
    return tag;
   
  }
  
  public boolean isBoolean() {
    return this == bool;
  }
  
  public boolean isNumber() {
    return this == number;
  }
  
  public boolean isString() {
    return this == string;
  }
  
  public String javaType() {
    return javaType;
  }
  
  public String javaTypePrimitive() {
    return javaType;
  }

}