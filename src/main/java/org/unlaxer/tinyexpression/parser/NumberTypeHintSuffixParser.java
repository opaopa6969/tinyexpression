package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {

  private static final long serialVersionUID = -2164382161036547415L;

  public NumberTypeHintSuffixParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return
      new Parsers(//
          Parser.get(AsParser.class), //
          Parser.get(NumberTypeHintParser.class)//
      );

  }
}