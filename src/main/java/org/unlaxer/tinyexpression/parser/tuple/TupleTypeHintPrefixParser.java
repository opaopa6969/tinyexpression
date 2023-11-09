package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
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
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Class<? extends TypeHint> typeHint() {
    return TupleTypeHintParser.class;
  }
}