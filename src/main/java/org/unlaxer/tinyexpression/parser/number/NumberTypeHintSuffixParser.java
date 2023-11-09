package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Parsed;
import org.unlaxer.Source;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.TokenList;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.AsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {

  private static final long serialVersionUID = -2164382161036547415L;

  public NumberTypeHintSuffixParser() {
    super();
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }
  
  @Override
  public Parsers getLazyParsers() {
    return
      new Parsers(//
          new Optional(
              Parser.get(AsParser.class) //
          ),
          Parser.get(NumberTypeHintParser.class)//
      );
  }
  
  public static Token createToken(Source rootSource , CodePointOffset position,TokenKind tokenKind) {
    
    Token asToken = AsParser.createToken(rootSource ,  position, tokenKind);
    position = position.newWithAdd(asToken.getSource().cursorRange().endIndexExclusive.getPosition());
    Token numberTypeHintToken = NumberTypeHintParser.createToken(rootSource, position, tokenKind);
    TokenList children = TokenList.of(asToken,numberTypeHintToken);
    
    return new Token(tokenKind, children, Parser.get(NumberTypeHintSuffixParser.class));
  }
}