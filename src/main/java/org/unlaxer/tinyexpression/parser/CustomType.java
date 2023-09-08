package org.unlaxer.tinyexpression.parser;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CustomType implements CustomTypeInterface{
  
  ExpressionType expressionType;
  List<CustomTypeInterface> children;
  public CustomType(ExpressionType expressionType, List<CustomTypeInterface> children) {
    super();
    this.expressionType = expressionType;
    this.children = children;
  }
  
  public CustomType(ExpressionType expressionType) {
    super();
    this.expressionType = expressionType;
    this.children = Collections.emptyList();
  }


  public String toString() {
    
    if(expressionType.isCollection()) {
      
      String collect = children.stream()
          .map(CustomTypeInterface::toString)
          .collect(Collectors.joining(","));
      
      return expressionType.javaType+"<"+collect +">";
    }
    return expressionType.javaType;
    
  }

  @Override
  public CustomTypeInterface get() {
    return this;
  }

  @Override
  public List<CustomTypeInterface> children() {
    return children;
  }
  
}