package org.unlaxer.tinyexpression.evaluator.javacode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.tinyexpression.parser.MethodParameterParser;
import org.unlaxer.tinyexpression.parser.TinyExpressionParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportParser;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationParser;

public class TinyExpressionTokens{
  
  final Token tinyExpressionToken;
  final List<Token> importTokens;
  final List<Token> variableDeclarationTokens;
  final List<Token> annotationTokens;
  final Token expressionToken;
  final Map<String,String> classNameByIdentifier;
  final Map<String,Token> variableDeclarationByVariableName;
  final Map<String,Token> methodDeclarationByVariableName;
  final List<Token> methodTokens;

  
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
    
    variableDeclarationByVariableName = variableDeclarationTokens.stream()
      .collect(Collectors.toMap(
        token->{
            Token extractVariableParserToOken = VariableDeclarationParser.extractVariableParserToken(token);
            VariableParser parser = extractVariableParserToOken.getParser(VariableParser.class);
            String variableName = parser.getVariableName(extractVariableParserToOken);
            return variableName;
        
        },
        Function.identity())
      );
    
    methodTokens = TinyExpressionParser.extractMethods(tinyExpressionToken);
    
    methodDeclarationByVariableName = methodTokens.stream()
       .collect(Collectors.toMap(
           token->{}, 
           
           null
           
           
           
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
  public List<Token> getMethodTokens() {
    return methodTokens;
  }

  public String resolveJavaClass(String classNameOrMethod) {
    String string = classNameByIdentifier.get(classNameOrMethod);
    return string == null ? classNameOrMethod : string;
  }
  
  public java.util.Optional<Token> matchedVariableDeclaration(String VariableName){
    
    Token token = variableDeclarationByVariableName.get(VariableName);
    return java.util.Optional.ofNullable(token);
  }
  
  public java.util.Optional<Token> matchedMethod(String VariableName){
    
    Token token = variableDeclarationByVariableName.get(VariableName);
    return java.util.Optional.ofNullable(token);
  }

  
  
}