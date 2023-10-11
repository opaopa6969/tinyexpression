package org.unlaxer.tinyexpression.parser.list;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ListExpressionParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(ImmediatelyListCreationParser.class),
        Parser.get(ListVariableParser.class)
    );
  }
  
}