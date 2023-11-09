package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.tinyexpression.parser.TypeHint;
import org.unlaxer.tinyexpression.parser.TypeHintVariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleParenthesesParser;

public class ListTypeHintPrefixParser extends JavaStyleParenthesesParser implements TypeHintVariableParser{

  
  public ListTypeHintPrefixParser() {
    super(Parser.get(ListTypeHintParser.class));
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return ListTypeHintParser.class;
  }
}