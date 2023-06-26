package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningNumberParser extends JavaStyleDelimitedLazyChain implements Returning {

//  static final String word = "returning";
//  static final WordParser wordParser = new WordParser(word);
  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new Optional(
            Parser.get(()->new WordParser("returning"))
        ),
        Parser.get(NumberTypeHintSuffixParser.class)
    );
  }
  
  @VirtualTokenCreator
  public static Token getReturningNumberParserWhenNotSpecifiedReturingClause(int position,
      Token sideEffectFirstParameter) {
    
    int current = position;
//    Token wordToken = new Token(TokenKind.virtualTokenConsumed, new RangedString(position, word), wordParser);
//    current += wordToken.tokenRange.endIndexExclusive;
    
    Token numberTypeHintSuffixToken = 
        NumberTypeHintSuffixParser.createToken(current, TokenKind.virtualTokenConsumed);
    current += numberTypeHintSuffixToken.tokenRange.endIndexExclusive;
    
//    Token defaultClauseToken = DefaultClauseParser.createToken(current, TokenKind.virtualTokenConsumed);
//    current += defaultClauseToken.tokenRange.endIndexExclusive;
 
    List<Token> children = List.of(/*wordToken , */ numberTypeHintSuffixToken /*defaultClauseToken*/, sideEffectFirstParameter);
    
    return new Token(TokenKind.virtualTokenConsumed, children, 
        Parser.get(ReturningNumberParser.class), position);
  }
}