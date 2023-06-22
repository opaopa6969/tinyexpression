package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;

public class NakedVariableBuilder implements TokenCodeBuilder{

  public static NakedVariableBuilder SINGLETON = new NakedVariableBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder, Token token ,
      TinyExpressionTokens tinyExpressionTokens) {
    
    String variableName = NakedVariableParser.getVariableName(token);
    builder.append("calculateContext.getFromNumberOrStringOrBoolean(").w(variableName).append(")");
    
  }
  
}