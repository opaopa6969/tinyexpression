package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class DescriptionParser extends JavaStyleDelimitedLazyChain implements NoExpression{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(()->new WordParser("description")),
        Parser.get(()->new WordParser("=")),
        Parser.get(StringLiteralParser.class)
    );
  }
  
}