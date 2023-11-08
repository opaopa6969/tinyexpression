package org.unlaxer.tinyexpression.parser.type;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;
import org.unlaxer.parser.elementary.WordParser;

public class TypeDefHeaderParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("typedef"),
        new WordParser("typeDefinition")
    );
  }
}

