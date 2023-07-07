package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberMethodParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(NumberTypeHintParser.class),
        Parser.get(IdentifierParser.class),
        Parser.get(MethodParametersParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(NumberExpressionParser.class),
        Parser.get(RightCurlyBraceParser.class)
    );
  }
}