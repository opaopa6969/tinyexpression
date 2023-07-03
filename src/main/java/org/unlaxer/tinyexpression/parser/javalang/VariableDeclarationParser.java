package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class VariableDeclarationParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(NumberVariableDeclarationParser.class),
        Parser.get(StringVariableDeclarationParser.class),
        Parser.get(BooleanVariableDeclarationParser.class)
    );
  }
  
}