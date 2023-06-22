package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class StringTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {

  private static final long serialVersionUID = -1244382161036541973L;

  public StringTypeHintSuffixParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        new Optional(
            Parser.get(AsParser.class) //
        ),
        Parser.get(StringTypeHintParser.class)//
    );
  }
}