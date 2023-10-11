package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class ListTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{

  public ListTypeHintPrefixParser() {
    super(Parser.get(ListTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return ListTypeHintParser.class;
  }
}