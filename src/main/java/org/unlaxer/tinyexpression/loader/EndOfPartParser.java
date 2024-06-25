package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.EndOfSourceParser;

public class EndOfPartParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(EndOfPartLineParser.class),
        Parser.get(EndOfSourceParser.class)
    );
  }
  
}