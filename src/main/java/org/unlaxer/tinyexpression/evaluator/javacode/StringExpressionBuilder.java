package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;

public class StringExpressionBuilder implements TokenCodeBuilder {

  public static final StringExpressionBuilder SINGLETON = new StringExpressionBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder , Token token , 
      TinyExpressionTokens tinyExpressionTokens) {
    
    ExpressionOrLiteral one = StringClauseBuilder.SINGLETON.build(token , tinyExpressionTokens);
    
    builder.append(one.toString());
  }
}