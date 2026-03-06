package org.unlaxer.tinyexpression.parser;

import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class ObjectTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser {
  private static final long serialVersionUID = -602438216103654412L;

  public ObjectTypeHintPrefixParser() {
    super(ObjectTypeHintParser.class);
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return ObjectTypeHintParser.class;
  }
}
