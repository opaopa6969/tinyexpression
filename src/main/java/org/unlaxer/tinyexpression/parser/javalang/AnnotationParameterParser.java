package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.EqualParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.tinyexpression.parser.bool.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.number.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class AnnotationParameterParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(IdentifierParser.class),
        Parser.get(EqualParser.class),
        new Choice(
          Parser.get(StringExpressionParser.class),
          Parser.get(BooleanExpressionParser.class),
          Parser.get(NumberExpressionParser.class)
        )
    );
  }
}