package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.ExpressionType;

public class NumberTypeHintParser extends LazyChoice {

  private static final long serialVersionUID = 411285131946664894L;

  public NumberTypeHintParser() {
    super();
  }

//  static final WordParser numberWordParser = new WordParser("number");


  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return
      new Parsers(

          new FloatNumberClassParser(),
          new NumberNumberClassParser(),
          new LongNumberClassParser(),
          new IntNumberClassParser(),
          new DoubleNumberClassParser(),
          new BigDecimalNumberClassParser(),
          new BigIntegerNumberClassParser(),
          new ByteNumberClassParser(),
          new ShortNumberClassParser()
      );
  }

  public static ExpressionType expressionType(TypedToken<NumberTypeHintParser> token) {

    return ChoiceInterface.choiced(token)
        .typed(NumberClassParser.class)
        .apply(NumberClassParser::expressionType);
  }

//  public static Token createToken(int position,TokenKind tokenKind) {
//
//    Token token = new Token(tokenKind, new RangedString(position, " number "), numberWordParser);
//    List<Token> children = List.of(token);
//    return new Token(tokenKind, children, Parser.get(NumberTypeHintParser.class),position);
//  }


//  public Token createToken(int position,TokenKind tokenKind) {
//
//
//    WordParser numberWordParser = numberWordParser();
//    String numberWord = " " + numberWordParser.word + " ";
//
//    Token token = new Token(tokenKind, new RangedString(position, numberWord), this);
//    List<Token> children = List.of(token);
//    return new Token(tokenKind, children, this ,position);
//  }

}