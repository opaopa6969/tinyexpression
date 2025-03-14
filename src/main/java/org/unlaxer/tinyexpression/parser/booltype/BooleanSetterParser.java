package org.unlaxer.tinyexpression.parser.booltype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.SetWordParser;
import org.unlaxer.tinyexpression.parser.SetterParser;
import org.unlaxer.tinyexpression.parser.StrictTypedBooleanExpressionParser;

public class BooleanSetterParser extends WhiteSpaceDelimitedLazyChain/*JavaStyleDelimitedLazyChain*/implements BooleanExpression, SetterParser{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(()->new Optional(IfNotExistsParser.class)),
        Parser.get(()->new Choice(
            Parser.newInstance(StrictTypedBooleanExpressionParser.class),
            Parser.get(BooleanExpressionParser.class)
          )
        )
    );
  }
}