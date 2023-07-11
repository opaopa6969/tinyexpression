package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyZeroOrMore;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class MethodsParser extends JavaStyleDelimitedLazyZeroOrMore{

  @Override
  public Supplier<Parser> targetParser() {
    return MethodChoiceParser::new;
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractMethods(Token thisParserParsed) {
    
    Parser.checkTokenParsedBySpecifiedParser(thisParserParsed , MethodsParser.class);
    
    List<Token> methods = thisParserParsed.flatten().stream()
        .filter(TokenPredicators.parserImplements(MethodParser.class))
        .collect(Collectors.toList());
    return methods;
  }
  
}