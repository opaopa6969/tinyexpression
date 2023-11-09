package org.unlaxer.tinyexpression.parser.number;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class NumberTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{
  private static final long serialVersionUID = -3513821610361471L;

  public NumberTypeHintPrefixParser() {
    super(Parser.get(NumberTypeHintParser.class));
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return NumberTypeHintParser.class;
  }
}