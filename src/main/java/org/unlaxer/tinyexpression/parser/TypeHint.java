package org.unlaxer.tinyexpression.parser;

import org.unlaxer.parser.Parser;

public interface TypeHint extends Parser{

  ExpressionType type();
}