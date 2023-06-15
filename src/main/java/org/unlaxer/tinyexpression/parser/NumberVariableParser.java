package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;

public class NumberVariableParser extends LazyChoice implements VariableParser , NumberExpression{

  private static final long serialVersionUID = -6048451001170410L;

  public NumberVariableParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberPrefixedVariableParser.class), //
          Parser.get(NumberSuffixedVariableParser.class)//
      );
  }
  
  public static String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    if(choiced.parser instanceof NumberPrefixedVariableParser) {
      return NumberPrefixedVariableParser.getVariableName(choiced);
    }else if(choiced.parser instanceof NumberSuffixedVariableParser) {
      return NumberSuffixedVariableParser.getVariableName(choiced);
    }
    throw new IllegalArgumentException();
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.number);
  }

}