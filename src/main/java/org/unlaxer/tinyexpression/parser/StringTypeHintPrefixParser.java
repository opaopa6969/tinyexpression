package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class StringTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{
  private static final long serialVersionUID = -784438216103654415L;

  public StringTypeHintPrefixParser() {
    super(StringTypeHintParser.class);
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return StringTypeHintParser.class;
  }
}