package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.EqualParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.tinyexpression.parser.booltype.BooleanExpressionParser;
import org.unlaxer.tinyexpression.parser.numbertype.NumberExpressionParser;
import org.unlaxer.tinyexpression.parser.stringtype.StringExpressionParser;

public class AnnotationParameterParser extends JavaStyleDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
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