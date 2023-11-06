package org.unlaxer.tinyexpression.parser.number;

import java.util.List;
import java.util.Optional;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class NumberSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements NumberExpression , VariableParser{

  private static final long serialVersionUID = -1060485506213097042L;

  public NumberSuffixedVariableParser() {
    super();
  }

  @Override
  public Parsers getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NakedVariableParser.class), //0
          Parser.get(NumberTypeHintSuffixParser.class)//1
      );
  }

  @Override
  public Optional<ExpressionType> typeAsOptional() {
    return Optional.of(ExpressionType.number);
  }
}