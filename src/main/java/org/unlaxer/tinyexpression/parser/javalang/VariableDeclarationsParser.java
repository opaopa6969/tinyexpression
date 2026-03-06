package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

@SuppressWarnings("serial")
public class VariableDeclarationsParser extends LazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return VariableDeclarationParser::new;
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractVariables(Token thisParserParsed){
    
    if(false == thisParserParsed.parser instanceof VariableDeclarationsParser) {
      throw new IllegalArgumentException(
          "Expected VariableDeclarationsParser token but got: "
              + thisParserParsed.parser.getClass().getName()
              + " (tokenPath=" + thisParserParsed.getPath() + ")");
    }
    
    boolean isOperatorOperandTree  = thisParserParsed.getChildren(TokenPredicators.parserImplements(VariableDeclaration.class))
        .findFirst().isPresent();
    
    Stream<Token> children = isOperatorOperandTree ?
        thisParserParsed.getAstNodeChildren().stream():
        thisParserParsed.getChildren(TokenPredicators.parsers(VariableDeclarationParser.class))
        .map(token->token.getChild(TokenPredicators.allMatch()));
    
    List<Token> collect1 = children.collect(Collectors.toList());
    
    List<Token> collect = collect1.stream().map(token->{
      AbstractVariableDeclarationParser parser = (AbstractVariableDeclarationParser) token.parser;
      Token extractVariable = parser.extractVariable(token);
      return extractVariable;
    }).collect(Collectors.toList());
    
    return collect;
  }
}
