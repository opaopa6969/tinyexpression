package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public enum ExpressionType{
  number("Float"),
  string("String"),
  bool("Boolean"),
  tuple("Tuple"),
  list("List"),
  map("Map")
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
  
  public boolean isTuple() {
    return this == tuple;
  }
  
  public boolean isList() {
    return this == list;
  }
  
  public boolean isMap() {
    return this == map;
  }
  
  public String javaType() {
    return javaType;
  }
  
  public boolean isCollection() {
    
    return this == map || this == list || this == tuple;
    
  }
}