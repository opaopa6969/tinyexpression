package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;

public class BooleanVariableParser extends LazyChoice {

  private static final long serialVersionUID = -60484510350410L;

  public BooleanVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(BooleanPrefixedVariableParser.class), //
          Parser.get(BooleanSuffixedVariableParser.class)//
      );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof BooleanPrefixedVariableParser) {
      return BooleanPrefixedVariableParser.getVariableName(thisParserParsed);
    }else if(choiced.parser instanceof BooleanSuffixedVariableParser) {
      return BooleanSuffixedVariableParser.getVariableName(thisParserParsed);
    }
    throw new IllegalArgumentException();
  }
}