package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class NumberSuffixedVariableParser extends WhiteSpaceDelimitedLazyChain implements Expression {

  private static final long serialVersionUID = -1060485506213097042L;

  List<Parser> parsers;

  public NumberSuffixedVariableParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(NakedVariableParser.class), //0
        Parser.get(NumberTypeHintSuffixParser.class)//1
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }

  public static String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.filteredChildren.get(0);
    return NakedVariableParser.getVariableName(token);
  }
}