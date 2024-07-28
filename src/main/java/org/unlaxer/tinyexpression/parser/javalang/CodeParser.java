package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.StartAndEndQuotedParser;
import org.unlaxer.util.annotation.TokenExtractor;

public class CodeParser extends StartAndEndQuotedParser{

  public CodeParser() {
    super(
        Parser.get(CodeStartParser.class), //
        new QuotedContentsParser(Parser.get(CodeEndParser.class)) , //
        Parser.get(CodeEndParser.class)
    );
  }
  
  public static CodeBlock extractCodeBlock(Token thisParserParsed) {
    return new CodeBlock(
        extractSchemeAndIdentifier(thisParserParsed),
        extractContents(thisParserParsed)
    );
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

  @TokenExtractor
  public static String extractContents(Token thisParserParsed) {
      String string = thisParserParsed.flatten().stream()
        .filter(token->token.parser.getClass() == QuotedContentsParser.class)
        .findFirst()
        .get().getToken().get();
      return string;
  }
  
  public static class CodeBlock{
    
    public final SchemeAndIdentifier schemeAndIdentifier;
    public final String code;
    public CodeBlock(SchemeAndIdentifier schemeAndIdentifier, String code) {
      super();
      this.schemeAndIdentifier = schemeAndIdentifier;
      this.code = code;
    }
  }
}
