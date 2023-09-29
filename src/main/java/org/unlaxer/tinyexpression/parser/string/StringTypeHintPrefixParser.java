package org.unlaxer.tinyexpression.parser.string;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class StringTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{
  private static final long serialVersionUID = -784438216103654415L;

  public StringTypeHintPrefixParser() {
    super(Parser.get(StringTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return StringTypeHintParser.class;
  }
}