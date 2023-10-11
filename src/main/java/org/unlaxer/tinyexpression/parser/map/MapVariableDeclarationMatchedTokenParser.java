package org.unlaxer.tinyexpression.parser.map;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;

public class MapVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public MapVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(MapVariableDeclarationParser.class));
  }
}