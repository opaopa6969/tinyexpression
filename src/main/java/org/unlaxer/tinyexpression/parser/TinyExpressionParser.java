package org.unlaxer.tinyexpression.parser;

import java.time.zone.ZoneOffsetTransitionRule.TimeDefinition;
import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.RootParserIndicator;
import org.unlaxer.tinyexpression.parser.javalang.AnnotationsParser;
import org.unlaxer.tinyexpression.parser.javalang.ImportsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.javalang.VariableDeclarationsParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class TinyExpressionParser extends JavaStyleDelimitedLazyChain implements RootParserIndicator{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImportsParser.class),
        Parser.get(VariableDeclarationsParser.class),
        Parser.get(AnnotationsParser.class),
        Parser.get(NumberExpressionParser.class)
    );
  }
  
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static List<Token> extractImports(Token thisParserParsed){
    
    Optional<Token> childWithParserAsOptional = thisParserParsed.getChildWithParserAsOptional(ImportsParser.class);
    
    return childWithParserAsOptional
      .map(ImportsParser::extractImports)
      .orElseGet(List::of);
  }
}