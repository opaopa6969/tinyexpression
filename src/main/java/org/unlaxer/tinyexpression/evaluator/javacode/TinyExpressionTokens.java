package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportParser;

public class TinyExpressionTokens{
  
  final Token tinyExpressionToken;
  final Token importsToken;
  final Token variableDeclarationsToken;
  final Token annotationsToken;
  final Token expressionToken;
  final Map<String,String> classNameByIdentifier;
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
    
    classNameByIdentifier = importsToken.getAstNodeChildren().stream()
      .collect(
        Collectors.toMap(
          importToken->(String)(ImportParser.extractIdentifier(importToken).getToken().orElse("")),
          importToken->(String)(ImportParser.extractJavaClassMethodOrClassName(importToken).getToken().orElse(""))
        )
      );
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
  
  public String resolveJavaClass(String className) {
    String string = classNameByIdentifier.get(className);
    return string == null ? className : string;
  }
  
  
}