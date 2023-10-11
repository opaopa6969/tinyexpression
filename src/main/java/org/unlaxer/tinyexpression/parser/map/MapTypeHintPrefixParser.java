package org.unlaxer.tinyexpression.parser.map;

import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class MapTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{

  private static final long serialVersionUID = -1914866537557152359L;

  public MapTypeHintPrefixParser() {
    super(Parser.get(MapTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return MapTypeHintParser.class;
  }
}