package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ReturningParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return
        new Parsers(
          Parser.get(()->new WordParser("returning")),
          Parser.get(TypeHintSuffixParser.class)
        );
  }
  
}