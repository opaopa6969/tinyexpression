package org.unlaxer.tinyexpression.parser.javatype;

import org.unlaxer.Parsed;
import org.unlaxer.Token;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.parser.ascii.PlusParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.MinusParser;
import org.unlaxer.tinyexpression.parser.VariableTypeSelectable;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.numbertype.NumberTermParser;
import org.unlaxer.tinyexpression.parser.numbertype.StrictTypedNumberTermParser;

public class JavaExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator , ExternalJavaClassExpression , VariableTypeSelectable{

  @Override
  public boolean hasNakedVariableParser() {
    return false;
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return getLazyParsers(true);
  }

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers(boolean withNakedVariable) {

    //　<javExpression>


    // <expression> ::= <term>[('+'|'-')<term>]*
    Parsers parsers = new Parsers();

    Class<? extends Parser> termParserClazz = withNakedVariable ?
      NumberTermParser.class:
      StrictTypedNumberTermParser.class;

    parsers.add(termParserClazz);

    parsers.add(new ZeroOrMore(
        new WhiteSpaceDelimitedChain(
            new Choice(
              Parser.get(PlusParser.class),
              Parser.get(MinusParser.class)
            ),
            Parser.get(termParserClazz)
          )
    ));

    return parsers;

  }

  @Override
  public ExpressionType expressionType(Token thisParserParsed) {
    //FIXME!
    return null;
  }


}