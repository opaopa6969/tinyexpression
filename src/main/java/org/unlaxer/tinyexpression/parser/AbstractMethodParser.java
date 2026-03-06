package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public abstract class AbstractMethodParser extends JavaStyleDelimitedLazyChain implements MethodParser{
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(returningParser()),
        Parser.get(IdentifierParser.class),
        Parser.get(MethodParametersParser.class),
        Parser.get(LeftCurlyBraceParser.class),
        Parser.get(expressionParser()),
        Parser.get(RightCurlyBraceParser.class)
    );
  }
  
  
}