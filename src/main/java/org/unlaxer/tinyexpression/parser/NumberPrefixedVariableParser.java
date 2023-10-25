package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements NumberExpression  , VariableParser{

  private static final long serialVersionUID = -600501238210309122L;

  public NumberPrefixedVariableParser() {
    super();
  }

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
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