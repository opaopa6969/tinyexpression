package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class NumberPrefixedVariableParser extends WhiteSpaceDelimitedLazyChain implements Expression {

  private static final long serialVersionUID = -600501238210309122L;

  public NumberPrefixedVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberTypeHintPrefixParser.class), //0
          Parser.get(NakedVariableParser.class)//1
      );
  }

  public static String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.filteredChildren.get(1);
    return NakedVariableParser.getVariableName(token);
  }
}