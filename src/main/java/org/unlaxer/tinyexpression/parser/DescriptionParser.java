package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class DescriptionParser extends WhiteSpaceDelimitedLazyChain implements NoExpression{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(()->new WordParser("description")),
        Parser.get(()->new WordParser("=")),
        Parser.get(StringLiteralParser.class)
    );
  }
  
}