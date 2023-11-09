package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.ReturningBooleanParser;
import org.unlaxer.tinyexpression.parser.SideEffectExpressionParser;

public class BooleanSideEffectExpressionParser extends SideEffectExpressionParser implements BooleanExpression{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parser typedReturningParser() {
    return Parser.get(ReturningBooleanParser.class);
  }
}