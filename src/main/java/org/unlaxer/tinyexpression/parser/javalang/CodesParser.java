package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;
import org.unlaxer.tinyexpression.parser.javalang.CodeParser.CodeBlock;
import org.unlaxer.util.annotation.TokenExtractor;

public class CodesParser extends LazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ()->Parser.get(CodeParser.class);
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }
  
  @TokenExtractor
  public static List<CodeBlock> extractCodeBlocksAsModel(Token thisParserParsed){
    return thisParserParsed.filteredChildren.stream()
      .map(CodeParser::extractCodeBlockAsModel)
      .collect(Collectors.toList());
  }
  
  @TokenExtractor
  public static List<Token> extractCodeBlocks(Token thisParserParsed){
    return thisParserParsed.filteredChildren.stream()
      .map(CodeParser::extractCodeBlock)
      .collect(Collectors.toList());
  }
  
}