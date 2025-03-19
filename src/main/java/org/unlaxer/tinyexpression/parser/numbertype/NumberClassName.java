package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.ExpressionType;

public class  NumberClassName{

  final String word;
  final boolean isPrimitive;
  final ExpressionType expressionType;
  final Class<? extends Number> javaType;
  public NumberClassName(String word, boolean isPrimitive , ExpressionType expressionType , Class<? extends Number> javaType) {
    super();
    this.word = word;
    this.isPrimitive = isPrimitive;
    this.expressionType = expressionType;
    this.javaType = javaType;
  }
  public String word() {
    return word;
  }
  public boolean isPrimitive() {
    return isPrimitive;
  }
  public ExpressionType expressionType() {
    return expressionType;
  }
  public Class<? extends Number> javaType() {
    return javaType;
  }
}