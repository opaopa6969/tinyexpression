package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ObjectTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {

  private static final long serialVersionUID = -602438216103654413L;

  public ObjectTypeHintSuffixParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new Optional(
            AsParser.class
        ),
        Parser.get(ObjectTypeHintParser.class)
    );
  }
}
