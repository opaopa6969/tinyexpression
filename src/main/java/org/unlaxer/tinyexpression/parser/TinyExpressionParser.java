package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationsParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class TinyExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImportsParser.class),
        Parser.get(VariableDeclarationsParser.class),
        Parser.get(AnnotationsParser.class),
        Parser.get(NumberExpressionParser.class)
    );
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
    if(false == tinyExpressionToken.parser instanceof TinyExpressionParser) {
      throw new IllegalArgumentException();
    }
    Optional<Token> childWithParserAsOptional = tinyExpressionToken.getChildWithParserAsOptional(VariableDeclarationsParser.class);
    
    List<Token> variableChildren = childWithParserAsOptional
      .map(VariableDeclarationsParser::extractVariables)
      .orElseGet(List::of);

    return variableChildren;
  }
  
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractVariablesToken(Token tinyExpressionToken) {
    if(false == tinyExpressionToken.parser instanceof TinyExpressionParser) {
      throw new IllegalArgumentException();
    }
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

}