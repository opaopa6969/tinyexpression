package org.unlaxer.tinyexpression.parser.list;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ListCreationEntryParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
       
       Parser.get(CommaSeparatedNumberExpressionsParser.class),
       Parser.get(CommaSeparatedStringExpressionsParser.class),
       Parser.get(CommaSeparatedBooleanExpressionsParser.class),
       Parser.get(CommaSeparatedTupleExpressionsParser.class),
       Parser.get(CommaSeparatedMapExpressionsParser.class),
       Parser.get(CommaSeparatedListExpressionsParser.class)
    );
  }
  
}

