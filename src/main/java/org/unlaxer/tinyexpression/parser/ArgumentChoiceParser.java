package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.bool.StrictTypedBooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.number.StrictTypedNumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StrictTypedStringExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class ArgumentChoiceParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.newInstance(StrictTypedBooleanExpressionParser.class),
        Parser.newInstance(StrictTypedStringExpressionParser.class),
        Parser.newInstance
        (StrictTypedNumberExpressionParser.class),
        Parser.get(NumberExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(StringExpressionParser.class)
    );
  }
  
}