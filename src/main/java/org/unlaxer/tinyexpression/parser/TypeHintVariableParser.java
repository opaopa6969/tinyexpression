package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public interface TypeHintVariableParser extends Parser{
  public Class<? extends TypeHint> typeHint();
}