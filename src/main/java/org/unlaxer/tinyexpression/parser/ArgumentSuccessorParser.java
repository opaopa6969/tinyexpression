package org.unlaxer.tinyexpression.parser;

import org.unlaxer.Token;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.util.annotation.TokenExtractor;

public class ArgumentSuccessorParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(CommaParser.class),
        Parser.get(ArgumentChoiceParser.class)
    );
  }
  
  @TokenExtractor
  public static Token extractParameter(Token thisParserParsed) {
    return thisParserParsed.getChildWithParser(ArgumentChoiceParser.class);
  }
}