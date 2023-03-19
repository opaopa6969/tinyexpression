package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class StringSuffixedVariableParser extends WhiteSpaceDelimitedLazyChain implements StringExpression {

  private static final long serialVersionUID = -1065885382103097042L;

  List<Parser> parsers;

  public StringSuffixedVariableParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(NakedVariableParser.class), //0
        Parser.get(StringTypeHintSuffixParser.class)//1
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