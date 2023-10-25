package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringSetterParser extends WhiteSpaceDelimitedLazyChain/*JavaStyleDelimitedLazyChain*/implements StringExpression, SetterParser{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(()->new Optional(IfNotExistsParser.class)),
        Parser.get(()->new Choice(
            Parser.newInstance(StrictTypedStringExpressionParser.class),
            Parser.get(StringExpressionParser.class)
          )
        )
    );
  }
}