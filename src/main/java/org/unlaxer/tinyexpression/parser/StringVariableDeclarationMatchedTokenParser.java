package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;
import org.unlaxer.tinyexpression.parser.javalang.StringVariableDeclarationParser;

public class StringVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public StringVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(StringVariableDeclarationParser.class));
  }
}