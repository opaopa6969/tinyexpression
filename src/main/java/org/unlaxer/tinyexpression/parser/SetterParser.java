package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class SetterParser extends WhiteSpaceDelimitedLazyChain implements NoExpression{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(()->new WordParser("set")),
        Parser.get(()->new Optional(new WordParser("if not exists"))),
        Parser.get(()->new Choice(
            Parser.get(BooleanClauseParser.class),
            Parser.get(StringExpressionParser.class),
            Parser.get(ExpressionParser.class)
          )
        )
    );
  }
  
}