package org.unlaxer.tinyexpression.parser;

import java.util.List;

public enum Opecodes implements Opecode{

  numberValue(ExpressionTypes.number,Arity.nullary),
  numberVariable(ExpressionTypes.number,Arity.unary,ExpressionTypes._string),
  numberPlus(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberMinus(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberMultiple(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberDivide(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),

  stringValue(ExpressionTypes._string,Arity.nullary),
  stringVariable(ExpressionTypes._string,Arity.unary,ExpressionTypes._string),
  stringPlus(ExpressionTypes._string,Arity.binary,ExpressionTypes._string,ExpressionTypes._string),

  booleanValue(ExpressionTypes._boolean,Arity.nullary),
  booleanVariable(ExpressionTypes._boolean,Arity.unary,ExpressionTypes._string),
  booleanEq(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanNotEq(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanAnd(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanOr(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanXor(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),

  ;
  private Opecodes(ExpressionType expressionType, Arity arity, List<ExpressionType> parameters) {
    this.expressionType = expressionType;
    this.arity = arity;
    this.parameters = parameters;
  }
  private Opecodes(ExpressionType expressionType, Arity arity, ExpressionType... parameters) {
    this.expressionType = expressionType;
    this.arity = arity;
    this.parameters = List.of(parameters);
  }

  final ExpressionType expressionType;
  final Arity arity;
  final List<ExpressionType> parameters;

  public ExpressionType expressionType() {
    return expressionType;
  }
  public Arity arity() {
    return arity;
  }
  public List<ExpressionType> parameters() {
    return parameters;
  }
}