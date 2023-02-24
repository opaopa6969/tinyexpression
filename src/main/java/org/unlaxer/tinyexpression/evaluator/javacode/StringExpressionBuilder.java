package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.evaluator.javacode.JavaCodeCalculator.CodeBuilder;

public class StringExpressionBuilder implements CodeBuilder {

  public static final StringExpressionBuilder SINGLETON = new StringExpressionBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder , Token token) {
    
    ExpressionOrLiteral one = StringClauseBuilder.SINGLETON.build(token);
    
    builder.append(one.toString());
  }
}