package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.ExpressionType;
import org.unlaxer.tinyexpression.parser.ExpressionTypes;
import org.unlaxer.tinyexpression.parser.NakedVariableParser;
import org.unlaxer.tinyexpression.parser.VariableParser;
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
  public ExpressionType expressionType() {
    return ExpressionTypes.number;
  }

}