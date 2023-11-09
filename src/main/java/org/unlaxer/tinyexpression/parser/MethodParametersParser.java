package org.unlaxer.tinyexpression.parser;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class MethodParametersParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(LeftParenthesisParser.class),
        new Optional(
           Parser.get(MethodParametersElementParser.class)
        ),
        Parser.get(RightParenthesisParser.class)
    );
  }
  
  @TokenExtractor
  public java.util.Optional<TypedToken<TypedVariableParser>> 
    extractTypedVariableParser(TypedToken<MethodParametersParser> thisParserParsed , String parameterName){

    return thisParserParsed.flatten().stream()
      .filter(TokenPredicators.parserImplements(TypedVariableParser.class))
      .map(token->token.typed(TypedVariableParser.class))
      .filter(typeToken->{
        TypedVariableParser parser = typeToken.getParser();
        String variableName = parser.getVariableName(typeToken);
        return variableName.equals(parameterName);
      })
      .map(typedToken->
        typedToken.newWithReplace(typedToken.getParser().getRootVariableParer()).typed(TypedVariableParser.class))
      .findFirst();
  }
}