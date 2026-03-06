package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class ObjectExpressionParser extends LazyChoice implements ExpressionInterface {

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ObjectVariableParser.class),
        Parser.get(NumberExpressionParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(StringExpressionParser.class)
    );
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes.object;
  }
}
