package org.unlaxer.tinyexpression.parser.type;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanTypeHintParser;
import org.unlaxer.tinyexpression.parser.list.ListParser;
import org.unlaxer.tinyexpression.parser.map.MapParser;
import org.unlaxer.tinyexpression.parser.number.NumberTypeHintParser;
import org.unlaxer.tinyexpression.parser.string.StringTypeHintParser;
import org.unlaxer.tinyexpression.parser.tuple.TupleParser;

public class TypeParameterParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StringTypeHintParser.class),
        Parser.get(NumberTypeHintParser.class),
        Parser.get(BooleanTypeHintParser.class),
        Parser.get(TupleParser.class),
        Parser.get(MapParser.class),
        Parser.get(ListParser.class)
    );
  }
}

