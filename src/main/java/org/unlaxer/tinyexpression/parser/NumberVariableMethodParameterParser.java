package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;

public class NumberVariableMethodParameterParser extends LazyChoice implements VariableParser , NumberExpression{


  public NumberVariableMethodParameterParser() {
    super();
  }
  
  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NumberPrefixedVariableParser.class), 
          Parser.get(NumberSuffixedVariableParser.class)
      );
  }
  
  @Override
  public String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    
    if(choiced.parser instanceof VariableParser) {
      VariableParser variableParser=  (VariableParser) choiced.parser;;
      return variableParser.getVariableName(choiced);
    }
    throw new IllegalArgumentException();
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.number);
  }
  
}