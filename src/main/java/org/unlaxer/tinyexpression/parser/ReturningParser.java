package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

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
  
}