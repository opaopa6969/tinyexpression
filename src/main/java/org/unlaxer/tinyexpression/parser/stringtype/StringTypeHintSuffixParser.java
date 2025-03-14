package org.unlaxer.tinyexpression.parser.stringtype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.AsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {

  private static final long serialVersionUID = -1244382161036541973L;

  public StringTypeHintSuffixParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(//
        new Optional(
            AsParser.class //
        ),
        Parser.get(StringTypeHintParser.class)//
    );
  }
  
}