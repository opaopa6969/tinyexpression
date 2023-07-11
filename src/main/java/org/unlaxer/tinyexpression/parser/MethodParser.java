package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.util.annotation.TokenExtractor;

public interface MethodParser extends Parser{
  
  public abstract Class<? extends TypeHint> returningParser();
  public abstract Class<? extends ExpressionInterface> expressionParser();

  @TokenExtractor
  public default Token returning(Token thisParserParsed) {
    
    checkTokenParsedByThisParser(thisParserParsed);
    
    return thisParserParsed.getChild(TokenPredicators.parsers(returningParser()));
  }
  
  @TokenExtractor
  public default Token methodName(Token thisParserParsed) {
    
    checkTokenParsedByThisParser(thisParserParsed);
    
    return thisParserParsed.getChild(TokenPredicators.parsers(IdentifierParser.class));
  }

  
}