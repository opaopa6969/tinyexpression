package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.ParenthesesParser;

public class NumberTypeHintPrefixParser extends ParenthesesParser {
  private static final long serialVersionUID = -3513821610361471L;

  public NumberTypeHintPrefixParser() {
    super(Parser.get(NumberTypeHintParser.class));
  }
}