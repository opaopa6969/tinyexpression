package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class NumberTypeHintPrefixParser extends JavaStyleParenthesesParser {
  private static final long serialVersionUID = -3513821610361471L;

  public NumberTypeHintPrefixParser() {
    super(NumberTypeHintParser.class);
  }

}