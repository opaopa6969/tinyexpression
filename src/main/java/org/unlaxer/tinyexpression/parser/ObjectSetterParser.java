package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.booltype.*;
import org.unlaxer.tinyexpression.parser.numbertype.*;
import org.unlaxer.tinyexpression.parser.stringtype.*;
import org.unlaxer.tinyexpression.parser.javatype.*;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ObjectSetterParser extends JavaStyleDelimitedLazyChain
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
