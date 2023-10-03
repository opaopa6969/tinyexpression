package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.referencer.MatchedTokenParser;

public class TupleVariableDeclarationMatchedTokenParser extends MatchedTokenParser{

  public TupleVariableDeclarationMatchedTokenParser() {
    super(TokenPredicators.parsers(TupleVariableDeclarationParser.class));
  }
}