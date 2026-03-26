package org.unlaxer.tinyexpression.parser;

import java.util.List;

public enum Opecodes implements Opecode{

  numberExpression(ExpressionTypes.number,Arity.nullary),
  numberExpressions(ExpressionTypes.number,Arity.multiary),
  numberValue(ExpressionTypes.number,Arity.nullary),
  numberVariable(ExpressionTypes.number,Arity.unary,ExpressionTypes._string),
  numberPlus(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberMinus(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberMultiple(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberDivide(ExpressionTypes.number,Arity.binary,ExpressionTypes.number,ExpressionTypes.number),
  numberIf(ExpressionTypes.number,Arity.ternary,ExpressionTypes._boolean,ExpressionTypes.number,ExpressionTypes.number),
  numberCaseFactor(ExpressionTypes.number,Arity.binary,ExpressionTypes._boolean,ExpressionTypes.number),
  numberCaseDefaultFactor(ExpressionTypes.number,Arity.unary,ExpressionTypes.number),
  numberCaseFactors(ExpressionTypes.number,Arity.multiary,numberCaseFactor),
  numberCase(ExpressionTypes.number,Arity.binary,numberCaseFactors,numberCaseDefaultFactor),
  

  stringValue(ExpressionTypes._string,Arity.nullary),
  stringVariable(ExpressionTypes._string,Arity.unary,ExpressionTypes._string),
  stringPlus(ExpressionTypes._string,Arity.binary,ExpressionTypes._string,ExpressionTypes._string),
  stringIf(ExpressionTypes._string,Arity.ternary,ExpressionTypes._boolean,ExpressionTypes._string,ExpressionTypes._string),

  booleanValue(ExpressionTypes._boolean,Arity.nullary),
  booleanVariable(ExpressionTypes._boolean,Arity.unary,ExpressionTypes._string),
  booleanEq(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanNotEq(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanAnd(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanOr(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanXor(ExpressionTypes._boolean,Arity.binary,ExpressionTypes._boolean,ExpressionTypes._boolean),
  booleanIf(ExpressionTypes._boolean,Arity.ternary,ExpressionTypes._boolean,ExpressionTypes._boolean,ExpressionTypes._boolean),

  ;
  
  private Opecodes(ExpressionType expressionType, Arity arity) {
    this.expressionType = expressionType;
    this.arity = arity;
    parameters = List.of();
    opecodes = List.of();
  }
  
  private Opecodes(ExpressionType expressionType, Arity arity, ExpressionType... parameters) {
    this.expressionType = expressionType;
    this.arity = arity;
    this.parameters = List.of(parameters);
    opecodes = List.of();
  }
  
  private Opecodes(ExpressionType expressionType, Arity arity, Opecode... opecodes) {
    this.expressionType = expressionType;
    this.arity = arity;
    this.parameters = List.of();
    this.opecodes = List.of(opecodes);
  }


  final ExpressionType expressionType;
  final Arity arity;
  final List<ExpressionType> parameters;
  final List<Opecode> opecodes;

  public ExpressionType expressionType() {
    return expressionType;
  }
  public Arity arity() {
    return arity;
  }
  public List<ExpressionType> parameters() {
    return parameters;
  }
  public List<Opecode> opecodes() {
    return opecodes;
  }
  
  
  
}