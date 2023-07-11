package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public enum ExpressionType{
  number("float"),
  string("String"),
  bool("boolean")
  ;
  final Tag tag;
  final String javaType;
  
  private ExpressionType(String javaType) {
    this.tag = Tag.of(this);
    this.javaType = javaType;
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
}