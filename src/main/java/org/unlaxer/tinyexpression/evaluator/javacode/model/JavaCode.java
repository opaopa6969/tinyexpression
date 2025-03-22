package org.unlaxer.tinyexpression.evaluator.javacode.model;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.elementary.SchemeAndIdentifier;
import org.unlaxer.parser.elementary.StartAndEndQuotedParser.QuotedContentsParser;
import org.unlaxer.tinyexpression.parser.javalang.CodeParser;
import org.unlaxer.tinyexpression.parser.javalang.CodeStartParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public record JavaCode(TypedToken<CodeParser> token , SchemeAndIdentifier schemeAndIdentifier , String code){

  /**
   * @param thisParserParsed
   * @return CodeBlock List
   */
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static JavaCode extractCodeBlocksAsModel(TypedToken<CodeParser> codeParserToken){

    return new JavaCode(codeParserToken,
        extractSchemeAndIdentifierAsModel(codeParserToken),
        extractContentsAsString(codeParserToken)
    );
  }

  @TokenExtractor
  public static SchemeAndIdentifier extractSchemeAndIdentifierAsModel(TypedToken<CodeParser> codeParserToken) {
    Token collect = codeParserToken.flatten().stream()
      .filter(TokenPredicators.parsers(CodeStartParser.class))
      .findFirst()
      .get();
    String string = collect.getToken().get().strip();
    String substring = string.substring("```".length());
    String[] split = substring.split(":");
    return new SchemeAndIdentifier(split[0],split[1]);
  }

  @TokenExtractor
  public static String extractContentsAsString(TypedToken<CodeParser> codeParserToken) {
      String string = codeParserToken.flatten().stream()
        .filter(token->token.parser.getClass() == QuotedContentsParser.class)
        .findFirst()
        .get().getToken().get();
      return string;
  }


}