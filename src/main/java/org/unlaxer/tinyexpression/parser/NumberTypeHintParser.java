package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.RangedString;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class NumberTypeHintParser extends LazyChoice implements TypeHint{

  private static final long serialVersionUID = 411285131946664894L;

  public NumberTypeHintParser() {
    super();
  }
  static final WordParser numberWordParser = new WordParser("number");

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(
          new WordParser("Number"), //
          numberWordParser, //
          new WordParser("Float"), //
          new WordParser("float")
      );
  }
  
  public static Token createToken(int position,TokenKind tokenKind) {
    
    Token token = new Token(tokenKind, new RangedString(position, " number "), numberWordParser);
    List<Token> children = List.of(token);
    return new Token(tokenKind, children, Parser.get(NumberTypeHintParser.class),position);
  }

  @Override
  public ExpressionTypes type() {
    return ExpressionTypes.number;
  }
    

}