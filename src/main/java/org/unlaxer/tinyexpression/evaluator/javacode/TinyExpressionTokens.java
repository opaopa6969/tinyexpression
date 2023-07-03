package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportParser;

public class TinyExpressionTokens{
  
  final Token tinyExpressionToken;
  final List<Token> importTokens;
  final List<Token> variableDeclarationTokens;
  final List<Token> annotationTokens;
  final Token expressionToken;
  final Map<String,String> classNameByIdentifier;
  
  public TinyExpressionTokens(Token tinyExpressionToken) {
    super();
    if(false ==tinyExpressionToken.parser instanceof TinyExpressionParser) {
      throw new IllegalArgumentException();
    }
    this.tinyExpressionToken = tinyExpressionToken;
    importTokens = TinyExpressionParser.extractImports(tinyExpressionToken);
    expressionToken = TinyExpressionParser.extractNumberExpression(tinyExpressionToken);
    
    variableDeclarationTokens = TinyExpressionParser.extractVariables(tinyExpressionToken);
    annotationTokens = TinyExpressionParser.extractAnnotaions(tinyExpressionToken);
    
    classNameByIdentifier = importTokens.stream()
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
  public List<Token> getImportTokens() {
    return importTokens;
  }
  public List<Token> getVariableDeclarationTokens() {
    return variableDeclarationTokens;
  }
  public List<Token> getAnnotationsToken() {
    return annotationTokens;
  }
  public Token getExpressionToken() {
    return expressionToken;
  }
  
  public String resolveJavaClass(String classNameOrMethod) {
    String string = classNameByIdentifier.get(classNameOrMethod);
    return string == null ? classNameOrMethod : string;
  }
  
  
}