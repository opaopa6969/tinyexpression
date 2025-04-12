package org.unlaxer.tinyexpression.evaluator.javacode.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.unlaxer.ast.ASTNodeMapping;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.Opecode;

public class ExpressionModel {
  ExpressionModel parent;
  final Opecode opecode;
  final ExpressionType expressionType;
  ExpressionModel conditionOperand;
  ExpressionModel leftOperand;
  ExpressionModel rightOperand;
  final List<ExpressionModel> operands = new ArrayList<>();
  final String value;

  public ExpressionModel(ExpressionModel parent , Opecode opecode,
      ExpressionModel leftOperand, ExpressionModel rightOperand) {
    super();
    this.parent = parent;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    conditionOperand = null;
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    leftOperand.setParent(this);
    rightOperand.setParent(this);
    value = null;
  }

  public ExpressionModel(ExpressionModel parent ,Opecode opecode,
      ExpressionModel leftOperand) {
    super();
    this.parent = parent;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    conditionOperand = null;
    this.leftOperand = leftOperand;
    this.rightOperand = null;
    leftOperand.setParent(this);
    value = null;
  }

  public ExpressionModel(ExpressionModel parent ,Opecode opecode, ExpressionType expressionType,
      String value) {
    super();
    this.parent = parent;
    this.opecode = opecode;
    this.expressionType = expressionType;
    conditionOperand = null;
    this.leftOperand = null;
    this.rightOperand = null;
    this.value = value;
  }

  public ExpressionModel(Opecode opecode,
      ExpressionModel leftOperand, ExpressionModel rightOperand) {
    super();
    this.parent = null;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    conditionOperand = null;
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    value = null;
    leftOperand.setParent(this);
    rightOperand.setParent(this);
  }

  public ExpressionModel(Opecode opecode,
      ExpressionModel leftOperand) {
    super();
    this.parent = null;
    this.opecode = opecode;
    this.expressionType = opecode.expressionType();
    conditionOperand = null;
    this.leftOperand = leftOperand;
    this.rightOperand = null;
    value = null;
    leftOperand.setParent(this);
  }

  public ExpressionModel(Opecode opecode, ExpressionType expressionType,
      String value) {
    super();
    this.parent = null;
    this.opecode = opecode;
    this.expressionType = expressionType;
    conditionOperand = null;
    this.leftOperand = null;
    this.rightOperand = null;
    this.value = value;
  }


  public ExpressionModel(Opecode opecode, ExpressionType expressionType, ExpressionModel leftOperand,
      ExpressionModel rightOperand, String value) {
    super();
    this.opecode = opecode;
    this.expressionType = expressionType;
    conditionOperand = null;
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    this.value = value;
    leftOperand.setParent(this);
    rightOperand.setParent(this);
  }


  public ExpressionModel(Opecode opecode, ExpressionType expressionType,
      ExpressionModel conditionOperand,
      ExpressionModel leftOperand,ExpressionModel rightOperand) {
    super();
    this.opecode = opecode;
    this.expressionType = expressionType;
    this.conditionOperand = conditionOperand;
    this.leftOperand = leftOperand;
    this.rightOperand = rightOperand;
    this.value = null;
    conditionOperand.setParent(this);
    leftOperand.setParent(this);
    rightOperand.setParent(this);
  }

  public ExpressionModel(ASTNodeMapping astNodeMapping,String value) {
    super();
    this.opecode = astNodeMapping.opecode;
    this.expressionType = astNodeMapping.opecode.expressionType();
    this.conditionOperand = null;
    this.leftOperand = null;
    this.rightOperand = null;
    this.value = value;
//    conditionOperand.setParent(this);
//    leftOperand.setParent(this);
//    rightOperand.setParent(this);
  }

  public Opecode opecode() {
    return opecode;
  }

  public ExpressionType expressionType() {
    return expressionType;
  }

  public Optional<ExpressionModel> conditionOperand() {
    return Optional.ofNullable(conditionOperand);
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

  public Optional<ExpressionModel> parent() {
    return Optional.ofNullable(parent);
  }

  public void setParent(ExpressionModel parent) {
    this.parent = parent;
  }
  public List<ExpressionModel> operands(){
    return operands;
  }

  public void setConditionOperand(ExpressionModel conditionOperand) {
    this.conditionOperand = conditionOperand;
  }

  public void setLeftOperand(ExpressionModel leftOperand) {
    this.leftOperand = leftOperand;
  }

  public void setRightOperand(ExpressionModel rightOperand) {
    this.rightOperand = rightOperand;
  }
}