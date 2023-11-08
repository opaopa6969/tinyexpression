package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class ExpressionsParser extends LazyChoice{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          Parser.get(NumberExpressionParser.class),
          Parser.get(StringExpressionParser.class),
          Parser.get(BooleanExpressionParser.class)//,
          //FIXME!
//          Parser.get(TupleExpressionParser.class),
//          Parser.get(MapExpressionParser.class),
//          Parser.get(ListExpressionParser.class)
      );
    }
    
  }