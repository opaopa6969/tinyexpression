package org.unlaxer.tinyexpression.parser.numbertype;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.TokenKind;
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
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(//
          new Optional(
              AsParser.class //
          ),
          Parser.get(NumberTypeHintParser.class)//
      );
  }

  public static Token createToken(int position,TokenKind tokenKind) {

    Token asToken = AsParser.createToken(position, tokenKind);
    position += asToken.tokenRange.endIndexExclusive;
    Token numberTypeHintToken = NumberTypeHintParser.createToken(position, tokenKind);
    List<Token> children = List.of(asToken,numberTypeHintToken);

    return new Token(tokenKind, children, Parser.get(NumberTypeHintSuffixParser.class),position);
  }
}