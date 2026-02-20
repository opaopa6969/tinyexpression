package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class ImportsParser extends LazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ImportParser::new;
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractImports(Token thisParserParsed){
    return thisParserParsed.getAstNodeChildren().stream()
      .map(ImportParser::extractImport)
      .collect(Collectors.toList());
  }

}