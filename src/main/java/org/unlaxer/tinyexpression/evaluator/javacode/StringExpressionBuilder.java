package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class StringExpressionBuilder implements TokenCodeBuilder {

  public static final StringExpressionBuilder SINGLETON = new StringExpressionBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder , Token token , 
      TinyExpressionTokens tinyExpressionTokens , ExpressionType resultType) {
    
    ExpressionOrLiteral one = StringClauseBuilder.SINGLETON.build(token , tinyExpressionTokens , resultType);
    
    builder.append(one.toString());
  }
}