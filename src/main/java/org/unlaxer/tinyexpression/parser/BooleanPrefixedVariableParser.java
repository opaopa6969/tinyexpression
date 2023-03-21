package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;

public class BooleanPrefixedVariableParser extends WhiteSpaceDelimitedLazyChain implements BooleanExpression {

  private static final long serialVersionUID = -600588538210309122L;

  List<Parser> parsers;

  public BooleanPrefixedVariableParser() {
    super();
  }

  @Override
  public void initialize() {
    parsers = new Parsers(//
        Parser.get(BooleanTypeHintPrefixParser.class), //0
        Parser.get(NakedVariableParser.class)//1
    );
  }

  @Override
  public List<Parser> getLazyParsers() {
    return parsers;
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.filteredChildren.get(1);
    return NakedVariableParser.getVariableName(token);
  }

}