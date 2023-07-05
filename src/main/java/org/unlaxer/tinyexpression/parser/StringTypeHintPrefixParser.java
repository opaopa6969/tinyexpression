package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class StringTypeHintPrefixParser extends JavaStyleParenthesesParser {
  private static final long serialVersionUID = -784438216103654415L;

  public StringTypeHintPrefixParser() {
    super(Parser.get(StringTypeHintParser.class));
  }
}