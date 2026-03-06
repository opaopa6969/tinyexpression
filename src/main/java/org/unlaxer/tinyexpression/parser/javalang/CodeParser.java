package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.elementary.SchemeAndIdentifier;
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
  
  @TokenExtractor
  public static CodeBlock extractCodeBlockAsModel(Token thisParserParsed) {
    return new CodeBlock(
        extractSchemeAndIdentifierAsModel(thisParserParsed),
        extractContentsAsString(thisParserParsed)
    );
  }
  
  @TokenExtractor
  public static Token extractCodeBlock(Token thisParserParsed) {
    
    Token schemeAndIdentifier = extractSchemeAndIdentifier(thisParserParsed);
    Token contents = extractContents(thisParserParsed);
    
    return thisParserParsed.newCreatesOf(schemeAndIdentifier,contents);
  }
  
  @TokenExtractor
  public static Token extractSchemeAndIdentifier(Token thisParserParsed) {
    Token token = thisParserParsed.flatten().stream()
      .filter(TokenPredicators.parsers(CodeStartParser.class))
      .findFirst()
      .get();
    return token;
  }
  
  @TokenExtractor
  public static SchemeAndIdentifier extractSchemeAndIdentifierAsModel(Token thisParserParsed) {
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
  public static String extractContentsAsString(Token thisParserParsed) {
      String string = thisParserParsed.flatten().stream()
        .filter(token->token.parser.getClass() == QuotedContentsParser.class)
        .findFirst()
        .get().getToken().get();
      return string;
  }
  
  @TokenExtractor
  public static Token extractContents(Token thisParserParsed) {
      return  thisParserParsed.flatten().stream()
        .filter(token->token.parser.getClass() == QuotedContentsParser.class)
        .findFirst()
        .get();
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
