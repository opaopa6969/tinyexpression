package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.ParenthesesParser;

public class StringTypeHintPrefixParser extends ParenthesesParser {
  private static final long serialVersionUID = -784438216103654415L;

  public StringTypeHintPrefixParser() {
    super(Parser.get(StringTypeHintParser.class));
  }
}