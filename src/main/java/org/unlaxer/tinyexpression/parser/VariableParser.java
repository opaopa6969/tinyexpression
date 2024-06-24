package org.unlaxer.tinyexpression.parser;

import java.util.Optional;

import org.unlaxer.Tag;
import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;

public interface VariableParser extends Parser{
  
  public Optional<ExpressionType> typeAsOptional(ParseContext parseContext);
  public default boolean hasType(ParseContext parseContext) {
    return typeAsOptional(parseContext).isPresent();
  }
  
//  public String getVariableName(Token thisParserParsed);
  
  public static Tag variableNameTag = new Tag("variableName");
  
  public default String getVariableName(TypedToken<? extends VariableParser> token) {
    Token identifierToken = token.flatten().stream()
      .filter(TokenPredicators.hasTag(variableNameTag))
      .findFirst().get();
    return identifierToken.tokenString.get();
  }
}