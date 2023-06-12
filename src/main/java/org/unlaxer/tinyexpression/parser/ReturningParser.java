package org.unlaxer.tinyexpression.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return
        new Parsers(
            Parser.get(ReturningNumberParser.class),
            Parser.get(ReturningBooleanParser.class),
            Parser.get(ReturningStringParser.class)
        );
  }
  
  @VirtualTokenCreator
  public static Token getReturningParserWhenNotSpecifiedReturingClause(
      int position , Optional<Token> sideEffectFirstParameter) {
    
    Token _sideEffectFirstParameter = sideEffectFirstParameter.orElseThrow(()->new IllegalArgumentException("parameter must be specufued"));
    
    // only ReturningNumberParser
    
    List<Token> children = new ArrayList<Token>();
    children.add(ReturningNumberParser
        .getReturningNumberParserWhenNotSpecifiedReturingClause(position,_sideEffectFirstParameter));
    return new Token(TokenKind.virtualTokenConsumed, children, Parser.get(ReturningParser.class),position);
  }
  
  
  
}