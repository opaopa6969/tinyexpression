package org.unlaxer.tinyexpression.parser.number;

import java.util.Optional;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements NumberExpression  , VariableParser{

  private static final long serialVersionUID = -600501238210309122L;

  public NumberPrefixedVariableParser() {
    super();
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberTypeHintPrefixParser.class), //0
          Parser.get(NakedVariableParser.class)//1
      );
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.number);
  }

}