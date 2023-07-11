package org.unlaxer.tinyexpression.parser;

import java.util.List;
import java.util.Optional;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class NumberSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements NumberExpression , VariableParser{

  private static final long serialVersionUID = -1060485506213097042L;

  public NumberSuffixedVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NakedVariableParser.class), //0
          Parser.get(NumberTypeHintSuffixParser.class)//1
      );
  }

  @TokenExtractor
  public String getVariableName(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return NakedVariableParser.getVariableNameFromNaked(token);
  }

  @Override
  public Optional<ExpressionType> type() {
    return Optional.of(ExpressionType.number);
  }
}