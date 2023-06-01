package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class BooleanSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements BooleanExpression {

  private static final long serialVersionUID = -1060485382103097042L;

  public BooleanSuffixedVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return 
      new Parsers(//
          Parser.get(NakedVariableParser.class), //
          Parser.get(BooleanTypeHintSuffixParser.class)//
      );
  }
  
  @TokenExtractor
  public static Token getVariableNameAsToken(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return token;
  }
  
  public static String getVariableName(Token thisParserParsed) {
    return NakedVariableParser.getVariableName(getVariableNameAsToken(thisParserParsed));
  }

}