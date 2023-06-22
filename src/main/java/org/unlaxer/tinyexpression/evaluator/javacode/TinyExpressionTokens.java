package org.unlaxer.tinyexpression.evaluator.javacode;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;

public class TinyExpressionTokens{
  
  final Token tinyExpressionToken;
  final Token importsToken;
  final Token variableDeclarationsToken;
  final Token annotationsToken;
  final Token expressionToken;
  public TinyExpressionTokens(Token tinyExpressionToken) {
    super();
    if(false ==tinyExpressionToken.parser instanceof TinyExpressionParser) {
      throw new IllegalArgumentException();
    }
    this.tinyExpressionToken = tinyExpressionToken;
    importsToken = TinyExpressionParser.extractImports(tinyExpressionToken);
    expressionToken = TinyExpressionParser.extractNumberExpression(tinyExpressionToken);
    
    variableDeclarationsToken = TinyExpressionParser.extractVariables(tinyExpressionToken);
    annotationsToken = TinyExpressionParser.extractAnnotaions(tinyExpressionToken);
  }
  public Token getTinyExpressionToken() {
    return tinyExpressionToken;
  }
  public Token getImportsToken() {
    return importsToken;
  }
  public Token getVariableDeclarationsToken() {
    return variableDeclarationsToken;
  }
  public Token getAnnotationsToken() {
    return annotationsToken;
  }
  public Token getExpressionToken() {
    return expressionToken;
  }
  
  
}