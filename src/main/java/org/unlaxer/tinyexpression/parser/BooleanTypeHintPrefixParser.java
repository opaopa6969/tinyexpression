package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.ParenthesesParser;

public class BooleanTypeHintPrefixParser extends ParenthesesParser {
  private static final long serialVersionUID = -6243821610365440L;

  public BooleanTypeHintPrefixParser() {
    super(Parser.get(BooleanTypeHintParser.class));
  }
}