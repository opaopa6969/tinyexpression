package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.Token.ChildrenKind;
import org.unlaxer.Token.SearchFirst;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenPredicators;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.AfterParse;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationsParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class TinyExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator , 
  AfterParse{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImportsParser.class),
        Parser.get(VariableDeclarationsParser.class),
        Parser.get(AnnotationsParser.class),
        new org.unlaxer.parser.combinator.Optional(
            Parser.get(NumberExpressionParser.class)
        ),
        Parser.get(MethodsParser.class)
    );
  }
  
  
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    Parsed parsed = super.parse(parseContext, tokenKind, invertMatch);
    return afterParse(parseContext, parsed, tokenKind, invertMatch);
  }

//  @Override
//  public Parsed afterParse(ParseContext parseContext, Parsed parsed, TokenKind tokenKind, boolean invertMatch) {
//    return parsed;
//  }

  @Override
  public Parsed afterParse(ParseContext parseContext, Parsed parsed, TokenKind tokenKind, boolean invertMatch) {
    if(parsed.isSucceeded()) {
     Token rootToken = parsed.getRootToken(true);
     Optional<Token> child1 = rootToken.getChildAsOptional(TokenPredicators.parsers(NumberExpressionParser.class));
     Optional<Token> child2 = rootToken.getChildAsOptional(TokenPredicators.parsers(MethodsParser.class));
     if(child1.isEmpty() && child2.isEmpty()) {
       parsed = parsed.negate().setMessage("specify method or expression");
       
     }
    }
    return parsed;
  }
  
  /**
   * @param thisParserParsed
   * @return token of restructure ImportsParser
   */
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractImports(Token thisParserParsed){
    
    Optional<Token> childWithParserAsOptional = thisParserParsed.getChildWithParserAsOptional(ImportsParser.class);
    
    List<Token> importChildren = childWithParserAsOptional
      .map(ImportsParser::extractImports)
      .orElseGet(List::of);
    
    return importChildren;
  }
  
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractImportsToken(Token thisParserParsed){
    
    List<Token> importChildren = extractImports(thisParserParsed);
    Token imports = new Token(TokenKind.consumed, importChildren, Parser.get(ImportsParser.class),0);
    return imports;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractNumberExpression(Token thisParserParsed){
    
    Token childWithParser = thisParserParsed.getChildWithParser(NumberExpressionParser.class);
    return childWithParser;
    
  }

  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractVariables(Token tinyExpressionToken) {
    
    Parser.checkTokenParsedBySpecifiedParser(tinyExpressionToken , TinyExpressionParser.class);
    
    Optional<Token> childWithParserAsOptional = tinyExpressionToken.getChildWithParserAsOptional(VariableDeclarationsParser.class);
    
    List<Token> variableChildren = childWithParserAsOptional
      .map(VariableDeclarationsParser::extractVariables)
      .orElseGet(List::of);

    return variableChildren;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractMethods(Token tinyExpressionToken) {
    
    Parser.checkTokenParsedBySpecifiedParser(tinyExpressionToken , TinyExpressionParser.class);
    
    Optional<Token> childWithParserAsOptional = tinyExpressionToken.getChildWithParserAsOptional(MethodsParser.class);
    
    List<Token> methodChildren = childWithParserAsOptional
      .map(MethodsParser::extractMethods)
      .orElseGet(List::of);

    return methodChildren;
  }
  
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractVariablesToken(Token tinyExpressionToken) {
    
    Parser.checkTokenParsedBySpecifiedParser(tinyExpressionToken , TinyExpressionParser.class);

    List<Token> variableChildren = extractVariables(tinyExpressionToken);
    Token variables = new Token(TokenKind.consumed, variableChildren, Parser.get(VariableDeclarationsParser.class),0);
    return variables;
  }

  public static List<Token> extractAnnotaions(Token tinyExpressionToken) {
    Optional<Token> childWithParserAsOptional = tinyExpressionToken.getChildWithParserAsOptional(AnnotationsParser.class);
    
    List<Token> annotationChildren = childWithParserAsOptional
      .map(AnnotationsParser::extractAnnotationss)
      .orElseGet(List::of);
    
    return annotationChildren;
  }
  
  public static Token extractAnnotaionsToken(Token tinyExpressionToken) {
    List<Token> extractAnnotaions = extractAnnotaions(tinyExpressionToken);
    Token variables = new Token(TokenKind.consumed, extractAnnotaions, Parser.get(AnnotationsParser.class),0);
    return variables;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractMethodsToken(Token tinyExpressionToken) {
    
    Parser.checkTokenParsedBySpecifiedParser(tinyExpressionToken , TinyExpressionParser.class);
    
    List<Token> methodChildren = extractMethods(tinyExpressionToken);
    Token methods = new Token(TokenKind.consumed, methodChildren, Parser.get(MethodsParser.class),0);
    return methods;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Optional<Token> returningTypeHint(Token tinyExpressionToken , String methodName){
    
    Parser.checkTokenParsedBySpecifiedParser(tinyExpressionToken , TinyExpressionParser.class);

    Token child = tinyExpressionToken.getChild(TokenPredicators.parsers(MethodsParser.class));
    List<Token> methods = child.flatten(SearchFirst.Breadth, ChildrenKind.astNodes).stream()
      .filter(TokenPredicators.parserImplements(MethodParser.class))
      .collect(Collectors.toList());
    for (Token token : methods) {
      MethodParser parser = (MethodParser) token.parser;
      String methodNameOfChild = parser.methodName(token).getToken().get();
      if(methodName.equals(methodNameOfChild)) {
        return Optional.of( token.getChildWithParser(parser.returningParser()));
      }
    }
    return Optional.empty();
  }
  
  
  
}