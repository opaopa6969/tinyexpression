package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class BooleanMethodParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(BooleanTypeHintParser.class),
        Parser.get(IdentifierParser.class),
        Parser.get(MethodParametersParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(BooleanExpressionParser.class),
        Parser.get(RightCurlyBraceParser.class)
    );
  }
}