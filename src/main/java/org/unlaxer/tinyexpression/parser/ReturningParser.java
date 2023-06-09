package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.util.annotation.VirtualTokenCreator;

public class ReturningParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return
        new Parsers(
            Parser.get(ReturningNumberParser.class),
            Parser.get(ReturningBooleanParser.class),
            Parser.get(ReturningStringParser.class)
        );
  }
  
  @VirtualTokenCreator
  public static Token getReturningParserWhenNotSpecifiedReturingClause() {
    
  }
  
}