package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;

public class NumberVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public NumberVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(NumberVariableDeclarationParser.class));
  }
}