package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Source;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenList;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return
        new Parsers(
            Parser.get(ReturningNumberParser.class),
            Parser.get(ReturningBooleanParser.class),
            Parser.get(ReturningStringParser.class)
        );
  }
  
  @VirtualTokenCreator
  public static Token getReturningParserWhenNotSpecifiedReturingClause(
      Source rootSource , 
      CodePointOffset position , Optional<Token> sideEffectFirstParameter) {
    
    Token _sideEffectFirstParameter = sideEffectFirstParameter.orElseThrow(()->new IllegalArgumentException("parameter must be specified"));
    
    // only ReturningNumberParser
    
    TokenList children = new TokenList();
    children.add(ReturningNumberParser
        .getReturningNumberParserWhenNotSpecifiedReturingClause(rootSource, position,_sideEffectFirstParameter));
    return new Token(TokenKind.virtualTokenConsumed, children, Parser.get(ReturningParser.class));
  }
  
  public interface Returning{
    
    public Class<?> returningType();
  }
}