package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.Name;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class TupleTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{

  private static final long serialVersionUID = -1914866537557152359L;

  public TupleTypeHintPrefixParser() {
    super(Parser.get(TupleTypeHintParser.class));
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return TupleTypeHintParser.class;
  }
}