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
  public static Token extractImports(Token thisParserParsed){
    
    Optional<Token> childWithParserAsOptional = thisParserParsed.getChildWithParserAsOptional(ImportsParser.class);
    
    List<Token> importChildren = childWithParserAsOptional
      .map(ImportsParser::extractImports)
      .orElseGet(List::of);
    
    Token imports = new Token(TokenKind.consumed, importChildren, Parser.get(ImportsParser.class),0);
    return imports;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractNumberExpression(Token thisParserParsed){
    
    Token childWithParser = thisParserParsed.getChildWithParser(NumberExpressionParser.class);
    return childWithParser;
    
  }

  public static Token extractVariables(Token tinyExpressionToken) {
    Optional<Token> childWithParserAsOptional = tinyExpressionToken.getChildWithParserAsOptional(VariableDeclarationsParser.class);
    
    List<Token> variableChildren = childWithParserAsOptional
      .map(VariableDeclarationsParser::extractVariables)
      .orElseGet(List::of);
    
    Token variables = new Token(TokenKind.consumed, variableChildren, Parser.get(VariableDeclarationsParser.class),0);
    return variables;
  }

  public static Token extractAnnotaions(Token tinyExpressionToken) {
    // TODO Auto-generated methasdsasadod stub
    asddsa
    return null;
  }
}