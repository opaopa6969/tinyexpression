package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;

public class StringVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public StringVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(StringVariableDeclarationParser.class));
  }
}