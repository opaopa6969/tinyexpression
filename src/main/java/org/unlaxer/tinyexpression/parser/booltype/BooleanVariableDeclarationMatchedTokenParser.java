package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;
import org.unlaxer.tinyexpression.parser.javalang.BooleanVariableDeclarationParser;

public class BooleanVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public BooleanVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(BooleanVariableDeclarationParser.class));
  }
}