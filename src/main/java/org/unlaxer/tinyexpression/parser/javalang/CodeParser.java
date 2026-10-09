package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.CodePointIndex;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.Choice;
import org.unlaxer.tinyexpression.codeblock.LongCodeFence;
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

  @Override
  public Parsers getLazyParsers() {
    return Parsers.of(new Choice(Parser.get(LongCodeBlockParser.class), new Chain(super.getLazyParsers())));
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
    Token extended = longToken(thisParserParsed);
    if (extended != null) return extendedPart(extended, false);
    Token token = thisParserParsed.flatten().stream()
      .filter(TokenPredicators.parsers(CodeStartParser.class))
      .findFirst()
      .get();
    return token;
  }
  
  @TokenExtractor
  public static SchemeAndIdentifier extractSchemeAndIdentifierAsModel(Token thisParserParsed) {
    Token collect = extractSchemeAndIdentifier(thisParserParsed);
    String string = collect.getToken().get().strip();
    int width = 0;
    while (width < string.length() && string.charAt(width) == '`') width++;
    String substring = string.substring(width);
    String[] split = substring.split(":");
    return new SchemeAndIdentifier(split[0],split[1]);
    
  }

  @TokenExtractor
  public static String extractContentsAsString(Token thisParserParsed) {
      return extractContents(thisParserParsed).getToken().orElse("");
  }
  
  @TokenExtractor
  public static Token extractContents(Token thisParserParsed) {
      Token extended = longToken(thisParserParsed);
      if (extended != null) return extendedPart(extended, true);
      return  thisParserParsed.flatten().stream()
        .filter(token->token.parser.getClass() == QuotedContentsParser.class)
        .findFirst()
        .get();
  }

  private static Token longToken(Token root) {
    return root.flatten().stream().filter(t -> t.parser instanceof LongCodeBlockParser).findFirst().orElse(null);
  }

  private static Token extendedPart(Token token, boolean body) {
    String source = token.source.sourceAsString();
    var layout = LongCodeFence.scan(source, 0);
    if (layout == null) throw new IllegalArgumentException("invalid extended CodeBlock token");
    int start = body ? layout.bodyStart() : 0;
    int end = body ? layout.bodyEnd() : layout.bodyStart();
    return new Token(token.tokenKind, token.source.subSource(
        new CodePointIndex(source.codePointCount(0, start)),
        new CodePointIndex(source.codePointCount(0, end))),
        body ? new QuotedContentsParser(Parser.get(CodeEndParser.class)) : Parser.get(CodeStartParser.class));
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
