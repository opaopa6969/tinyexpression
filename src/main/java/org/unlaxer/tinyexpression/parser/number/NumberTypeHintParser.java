package org.unlaxer.tinyexpression.parser.number;

import java.util.List;

import org.unlaxer.CodePointOffset;
import org.unlaxer.Source;
import org.unlaxer.StringSource;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class NumberTypeHintParser extends LazyChoice implements TypeHint{

  private static final long serialVersionUID = 411285131946664894L;

  public NumberTypeHintParser() {
    super();
  }
  static final WordParser numberWordParser = new WordParser("number");

  @Override
  public Parsers getLazyParsers() {
    return
      new Parsers(
          new WordParser("Number"), //
          numberWordParser, //
          new WordParser("Float"), //
          new WordParser("float")
      );
  }
  
  public static Token createToken(Source rootSource , CodePointOffset position,TokenKind tokenKind) {
    
    Token token = new Token(tokenKind, StringSource.createSubSource(" number " , rootSource , position), numberWordParser);
    List<Token> children = List.of(token);
    return new Token(tokenKind, children, Parser.get(NumberTypeHintParser.class),position);
  }

  @Override
  public ExpressionType type() {
    return ExpressionType.number;
  }
    

}