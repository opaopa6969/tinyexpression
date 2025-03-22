package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;

public class ImportsParser extends LazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ImportParser::new;
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }

//  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
//  public static List<Token> extractImports(Token thisParserParsed){
//    return thisParserParsed.getAstNodeChildren().stream()
//      .map(ImportParser::extractImport)
//      .collect(Collectors.toList());
//  }

}