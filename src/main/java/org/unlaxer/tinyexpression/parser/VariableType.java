package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;

public enum VariableType{
  number,
  string,
  bool
  ;
  final Tag tag;
  
  private VariableType() {
    this.tag = Tag.of(this);
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
}