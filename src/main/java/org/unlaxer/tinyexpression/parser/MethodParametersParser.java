package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class MethodParametersParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(LeftParenthesisParser.class),
        new Optional(
           Parser.get(MethodParametersElementParser.class)
        ),
        Parser.get(RightParenthesisParser.class)
    );
  }
  
  public java.util.Optional<TypedToken<MethodParametersElementParser>> 
    extractParameterElementss(TypedToken<MethodParametersParser> thisParserParsed){
    
    return thisParserParsed.getChildAsOptional(TokenPredicators.parsers(MethodParametersElementParser.class))
      .map(token->token.typed(MethodParametersElementParser.class));
  }
}