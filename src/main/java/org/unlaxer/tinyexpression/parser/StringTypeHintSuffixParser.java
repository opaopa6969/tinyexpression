package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringTypeHintSuffixParser extends WhiteSpaceDelimitedLazyChain {

  private static final long serialVersionUID = -1244382161036541973L;

  List<Parser> parsers;

  public StringTypeHintSuffixParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(AsParser.class), //
        Parser.get(StringTypeHintParser.class)//
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}