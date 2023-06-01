package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class NumberTypeHintPrefixParser extends JavaStyleParenthesesParser {
  private static final long serialVersionUID = -3513821610361471L;

  public NumberTypeHintPrefixParser() {
    super(Parser.get(NumberTypeHintParser.class));
  }
}