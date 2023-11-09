package org.unlaxer.tinyexpression.parser.bool;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parsers;

public class BooleanExpressionParser extends AbstractBooleanExpressionParser{

  @Override
  public boolean hasNakedVariableParser() {
    return true;
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }
  
  @Override
  public Parsers getLazyParsers() {
    return getLazyParsers(true);
  }
  
}