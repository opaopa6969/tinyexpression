package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class BooleanTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser {
  private static final long serialVersionUID = -6243821610365440L;

  public BooleanTypeHintPrefixParser() {
    super(Parser.get(BooleanTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return BooleanTypeHintParser.class;
  }
}