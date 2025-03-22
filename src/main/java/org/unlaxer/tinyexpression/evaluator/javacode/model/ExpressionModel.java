package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.Optional;

import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.Opecode;

public class ExpressionModel {
//    public final ExpressionModel parent;
  final Opecode opecode;
  final ExpressionType expressionType;
  final ExpressionModel leftOperand;
  final ExpressionModel rightOperand;
  final String value;

  public ExpressionModel(/* ExpressionModel parent , */Opecode opecode,
      ExpressionModel leftOperand, ExpressionModel rightOperand) {
    super();
//      this.parent = parent;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    value = null;
  }

  public ExpressionModel(/* ExpressionModel parent , */Opecode opecode,
      ExpressionModel leftOperand) {
    super();
//      this.parent = parent;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    this.leftOperand = leftOperand;
    this.rightOperand = null;
    value = null;
  }

  public ExpressionModel(/* ExpressionModel parent , */Opecode opecode, ExpressionType expressionType,
      String value) {
    super();
//      this.parent = parent;
    this.opecode = opecode;
    this.expressionType = expressionType;
    this.leftOperand = null;
    this.rightOperand = null;
    this.value = value;
  }

  public ExpressionModel(Opecode opecode, ExpressionType expressionType, ExpressionModel leftOperand,
      ExpressionModel rightOperand, String value) {
    super();
    this.opecode = opecode;
    this.expressionType = expressionType;
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    this.value = value;
  }

  public Opecode opecode() {
    return opecode;
  }

  public ExpressionType expressionType() {
    return expressionType;
  }

  public Optional<ExpressionModel> leftOperand() {
    return Optional.ofNullable(leftOperand);
  }

  public Optional<ExpressionModel> rightOperand() {
    return Optional.ofNullable(rightOperand);
  }

  public Optional<String> getValue() {
    return Optional.of(value);
  }
}