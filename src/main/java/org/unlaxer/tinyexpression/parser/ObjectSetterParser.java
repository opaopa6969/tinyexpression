package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class ObjectSetterParser extends WhiteSpaceDelimitedLazyChain
    implements ExpressionInterface, SetterParser {

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(() -> new Optional(IfNotExistsParser.class)),
        Parser.get(() -> new Choice(
            Parser.get(NumberExpressionParser.class),
            Parser.get(BooleanExpressionParser.class),
            Parser.get(StringExpressionParser.class)
        ))
    );
  }

  @Override
  public ExpressionTypes expressionType() {
    return ExpressionTypes.object;
  }
}
