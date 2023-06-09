package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningNumberParser extends JavaStyleDelimitedLazyChain {

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(()->new WordParser("returning")),
        Parser.get(NumberTypeHintSuffixParser.class),
        Parser.get(DefaultClauseParser.class),
        Parser.get(ExpressionParser.class)
    );
  }
  
  @VirtualTokenCreator
  public static Token getReturningNumberParserWhenNotSpecifiedReturingClause() {
    
  }
}