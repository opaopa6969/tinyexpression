package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class SetterParser extends JavaStyleDelimitedLazyChain implements NoExpression{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(()->new WordParser("set")),
        Parser.get(()->new Optional(new WordParser("if not exists"))),
        Parser.get(()->new Choice(
            Parser.newInstance(StrictTypedBooleanExpressionParser.class),
            Parser.newInstance(StrictTypedStringExpressionParser.class),
            Parser.newInstance(StrictTypedNumberExpressionParser.class),
            Parser.get(BooleanExpressionParser.class),
            Parser.get(StringExpressionParser.class),
            Parser.get(NumberExpressionParser.class)
          )
        )
    );
  }
  
}