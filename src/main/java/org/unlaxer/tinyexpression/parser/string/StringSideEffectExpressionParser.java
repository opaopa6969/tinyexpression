package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ReturningStringParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class StringSideEffectExpressionParser extends SideEffectExpressionParser implements StringExpression{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parser typedReturningParser() {
    return Parser.get(ReturningStringParser.class);
  }
}