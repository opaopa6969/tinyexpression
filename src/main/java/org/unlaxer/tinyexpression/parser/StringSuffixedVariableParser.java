package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class StringSuffixedVariableParser extends JavaStyleDelimitedLazyChain implements StringExpression {

  private static final long serialVersionUID = -1065885382103097042L;

  public StringSuffixedVariableParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        Parser.get(NakedVariableParser.class), //0
        Parser.get(StringTypeHintSuffixParser.class)//1
    );
  }

  
  @TokenExtractor
  static Token getVariableNameToken(Token thisParserParsed) {
    Token token = thisParserParsed.getChildWithParser(NakedVariableParser.class);
    return token;
  }

  public static String getVariableName(Token thisParserParsed) {
    return NakedVariableParser.getVariableName(getVariableNameToken(thisParserParsed));
  }
}