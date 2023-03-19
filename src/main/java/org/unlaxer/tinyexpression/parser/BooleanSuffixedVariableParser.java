package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanSuffixedVariableParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression {

  private static final long serialVersionUID = -1060485382103097042L;

  List<Parser> parsers;

  public BooleanSuffixedVariableParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(NakedVariableParser.class), //
        Parser.get(BooleanTypeHintSuffixParser.class)//
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