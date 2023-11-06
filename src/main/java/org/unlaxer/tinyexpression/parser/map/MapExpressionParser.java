package org.unlaxer.tinyexpression.parser.map;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class MapExpressionParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ImmediatelyMapCreationParser.class),
        Parser.get(MapVariableParser.class)
    );
  }
  
}