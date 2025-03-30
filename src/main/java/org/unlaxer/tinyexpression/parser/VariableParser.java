package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;

public interface VariableParser extends Parser {

//  public String getVariableName(Token thisParserParsed);

  public static Tag variableNameTag = new Tag("variableName");

  public static String getVariableName(TypedToken<? extends VariableParser> token) {
    Token identifierToken = token.flatten().stream()
        .filter(TokenPredicators.hasTag(variableNameTag))
        .findFirst().get();
    return identifierToken.tokenString.get();
  }

}