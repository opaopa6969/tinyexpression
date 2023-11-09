package org.unlaxer.tinyexpression.parser.map;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.IfNotExistsParser;
import org.unlaxer.tinyexpression.parser.SetWordParser;
import org.unlaxer.tinyexpression.parser.SetterParser;

public class MapSetterParser extends WhiteSpaceDelimitedLazyChain implements SetterParser{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(SetWordParser.class),
        Parser.get(()->new org.unlaxer.parser.combinator.Optional(
            Parser.get(IfNotExistsParser.class))
        ),
        Parser.get(()->new Choice(
            Parser.newInstance(MapExpressionParser.class)
          )
        )
    );
  }
  
}