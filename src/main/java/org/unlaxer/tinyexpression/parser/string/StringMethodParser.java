package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.tinyexpression.parser.AbstractMethodParser;
import org.unlaxer.tinyexpression.parser.ExpressionInterface;
import org.unlaxer.tinyexpression.parser.TypeHint;

public class StringMethodParser extends AbstractMethodParser{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Class<? extends TypeHint> returningParser() {
    return StringTypeHintParser.class;
  }

  @Override
  public Class<? extends ExpressionInterface> expressionParser() {
    return StringExpressionParser.class;
  }
}