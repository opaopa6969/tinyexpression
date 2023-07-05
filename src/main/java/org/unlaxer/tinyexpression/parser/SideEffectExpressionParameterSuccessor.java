package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class SideEffectExpressionParameterSuccessor extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(CommaParser.class),
        Parser.get(SideEffectExpressionParameterChoice.class)
    );
  }
  
  @TokenExtractor
  public static Token extractParameter(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(SideEffectExpressionParameterChoice.class);
  }
}