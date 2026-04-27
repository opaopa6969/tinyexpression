package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class IfNotExistsParser extends JavaStyleDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("if"),
        new WordParser("not"),
        new WordParser("exists")
    );
  }
}
