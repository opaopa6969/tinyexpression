package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.ChoiceInterface;
import org.unlaxer.parser.combinator.LazyChoice;

public class StringVariableMethodParameterParser extends LazyChoice implements VariableParser , StringExpression{


  public StringVariableMethodParameterParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(StringPrefixedVariableParser.class),//
        Parser.get(StringSuffixedVariableParser.class)
    );
  }
  
  public String getVariableName(Token thisParserParsed) {
    Token choiced = ChoiceInterface.choiced(thisParserParsed);
    VariableParser parser = choiced.getParser(VariableParser.class);
    return parser.getVariableName(choiced);
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.string);
  }
}