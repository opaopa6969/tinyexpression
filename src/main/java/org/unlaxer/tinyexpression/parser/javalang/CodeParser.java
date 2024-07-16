package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.StartAndEndQuotedParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class CodeParser extends StartAndEndQuotedParser{

  public CodeParser() {
    super(Parser.get(CodeStartParser.class), Parser.get(CodeEndParser.class) , new WordParser("```"));
  }
  
  @TokenExtractor
  public static SchemeAndIdentifier extractSchemeAndIdentifier(Token thisParserParsed) {
    Token collect = thisParserParsed.flatten().stream()
      .filter(TokenPredicators.parsers(CodeStartParser.class))
      .findFirst()
      .get();
    String string = collect.getToken().get().strip();
    String substring = string.substring("```".length());
    String[] split = substring.split(":");
    return new SchemeAndIdentifier(split[0],split[1]);
    
  }

  
}
