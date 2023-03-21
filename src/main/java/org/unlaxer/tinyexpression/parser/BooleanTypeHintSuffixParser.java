package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanTypeHintSuffixParser extends WhiteSpaceDelimitedLazyChain {

  private static final long serialVersionUID = -13382161034141973L;

  List<Parser> parsers;

  public BooleanTypeHintSuffixParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(AsParser.class), //
        Parser.get(BooleanTypeHintParser.class)//
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
}