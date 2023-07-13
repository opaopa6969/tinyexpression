package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class NumberTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{
  private static final long serialVersionUID = -3513821610361471L;

  public NumberTypeHintPrefixParser() {
    super(Parser.get(NumberTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return NumberTypeHintParser.class;
  }
}