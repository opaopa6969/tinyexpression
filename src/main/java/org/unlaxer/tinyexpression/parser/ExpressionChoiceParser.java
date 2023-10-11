package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.list.ListCreationParser;
import org.unlaxer.tinyexpression.parser.map.MapCreationParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;
import org.unlaxer.tinyexpression.parser.tuple.TupleCreationParser;

public class ExpressionChoiceParser extends LazyChoice{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(NumberExpressionParser.class),
        Parser.get(StringExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(TupleCreationParser.class),
        Parser.get(MapCreationParser.class),
        Parser.get(ListCreationParser.class)
    );
  }
}
