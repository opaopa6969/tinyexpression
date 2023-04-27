package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;

public class StringVariableParser extends LazyChoice {

  private static final long serialVersionUID = -604853821610350410L;

  public StringVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(StringPrefixedVariableParser.class), //
        Parser.get(StringSuffixedVariableParser.class)//
    );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof StringPrefixedVariableParser) {
      return StringPrefixedVariableParser.getVariableName(thisParserParsed);
    }else if(choiced.parser instanceof StringSuffixedVariableParser) {
      return StringSuffixedVariableParser.getVariableName(thisParserParsed);
    }
    throw new IllegalArgumentException();
  }

}