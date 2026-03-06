package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.ArgumentsParser;
import org.unlaxer.tinyexpression.parser.MethodInvocationParser;

public class MethodInvocationBuilder implements TokenCodeBuilder{
  
  public static final MethodInvocationBuilder SINGLETON = new MethodInvocationBuilder();

  @Override
  public void build(SimpleJavaCodeBuilder builder, Token token, 
      TinyExpressionTokens tinyExpressionTokens) {
    
    String methodNameAsString = MethodInvocationParser.getMethodNameAsString(token);
    Optional<Token> parametersClause = MethodInvocationParser.getParametersClause(token);
    List<Token> parameterTokens =
        parametersClause.isEmpty() ? 
        Collections.emptyList():
        ArgumentsParser.parameterTokens(methodNameAsString, parametersClause.get(), tinyExpressionTokens);
    
    builder
      .append(methodNameAsString)
      .append("(calculateContext");
    
    if(false == parameterTokens.isEmpty()) {
      builder
        .append(",");
    }
    ParametersBuilder.buildParameter(builder, parameterTokens, tinyExpressionTokens);
    builder
      .append(")");
     return ;
  }
  
}