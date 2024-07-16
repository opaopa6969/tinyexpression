package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;
import org.unlaxer.tinyexpression.parser.javalang.NumberVariableDeclarationParser;

public class NumberVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public NumberVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(NumberVariableDeclarationParser.class));
  }
}