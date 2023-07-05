package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberPrefixedVariableParser extends JavaStyleDelimitedLazyChain implements NumberExpression  , VariableParser{

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

  @TokenExtractor
  public String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return NakedVariableParser.getVariableNameFromNaked(token);
  }

  @Override
  public Optional<VariableType> type() {
    return Optional.of(VariableType.number);
  }

}