package org.unlaxer.tinyexpression.parser;

import java.util.Optional;
import java.util.stream.Stream;

import org.unlaxer.Token.ScanDirection;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class MethodParametersElementParser extends LazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(MethodParameterParser.class),
        new ZeroOrMore(
            new JavaStyleDelimitedLazyChain() {

              @Override
              public org.unlaxer.parser.Parsers getLazyParsers() {
                return new Parsers(
                    Parser.get(CommaParser.class),
                    Parser.get(MethodParameterParser.class)
                );
              }
            }
        )
    );
  }
  
  @TokenExtractor
  public Stream<TypedToken<TypedVariableParser>> typedVariableParsersAsStream(TypedToken<MethodParametersElementParser> thisParserParsed){
   
    //最終的にExpressionTypeを得てOperatorOperandTreeCreatorで型解決を行う
    return thisParserParsed.flatten(ScanDirection.Breadth).stream()
          .filter(TokenPredicators.parserImplements(TypedVariableParser.class))
          .map(token->token.typed(TypedVariableParser.class));
  }
  
  @TokenExtractor
  public Optional<TypedToken<TypedVariableParser>> typedVariableParsers(
      TypedToken<MethodParametersElementParser> thisParserParsed, String parameterName){
   
    //最終的にExpressionTypeを得てOperatorOperandTreeCreatorで型解決を行う
    Optional<TypedToken<TypedVariableParser>> collect = 
        typedVariableParsersAsStream(thisParserParsed)
          .filter(token->parameterName.equals(token.getParser().getVariableName(token)))
          .findFirst();
    
    return collect;
  }

}