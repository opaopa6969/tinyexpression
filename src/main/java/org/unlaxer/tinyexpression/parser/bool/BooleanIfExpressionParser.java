package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.IfExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpression;

public class BooleanIfExpressionParser extends IfExpressionParser implements NumberExpression{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Class<? extends Parser> strictTypedReturning() {
    return StrictTypedBooleanExpressionParser.class;
  }

  @Override
  public Class<? extends Parser> nonStrictTypedReturning() {
    return BooleanExpressionParser.class;
  }
}