package org.unlaxer.tinyexpression.parser;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Source;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenList;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ReturningParser.Returning;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberTypeHintSuffixParser;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningNumberParser extends JavaStyleDelimitedLazyChain implements Returning {

//  static final String word = "returning";
//  static final WordParser wordParser = new WordParser(word);
  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            Parser.get(()->new WordParser("returning"))
        ),
        Parser.get(NumberTypeHintSuffixParser.class)
    );
  }
  
  @VirtualTokenCreator
  public static Token getReturningNumberParserWhenNotSpecifiedReturingClause(Source rootSource , CodePointOffset position,
      Token sideEffectFirstParameter) {
    
    CodePointOffset current = position;
//    Token wordToken = new Token(TokenKind.virtualTokenConsumed, new RangedString(position, word), wordParser);
//    current += wordToken.tokenRange.endIndexExclusive;
    
    Token numberTypeHintSuffixToken = 
        NumberTypeHintSuffixParser.createToken(rootSource, current, TokenKind.virtualTokenConsumed);
    current = current.newWithAdd(
        numberTypeHintSuffixToken.getSource().cursorRange().endIndexExclusive.getPosition());
    
//    Token defaultClauseToken = DefaultClauseParser.createToken(current, TokenKind.virtualTokenConsumed);
//    current += defaultClauseToken.tokenRange.endIndexExclusive;
 
    TokenList children = TokenList.of(
        /*wordToken , */ numberTypeHintSuffixToken ,
        /*defaultClauseToken*/ sideEffectFirstParameter);
    
    return new Token(TokenKind.virtualTokenConsumed, children, 
        Parser.get(ReturningNumberParser.class));
  }

  @Override
  public Class<?> returningType() {
    return float.class;
  }
}