package org.unlaxer.tinyexpression.evaluator.javacode.model;

import org.unlaxer.TypedToken;
import org.unlaxer.parser.elementary.SchemeAndIdentifier;
import org.unlaxer.tinyexpression.parser.javalang.CodeParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public record JavaCode(TypedToken<CodeParser> token , SchemeAndIdentifier schemeAndIdentifier , String code){

  /**
   * @param codeParserToken
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
    return CodeParser.extractSchemeAndIdentifierAsModel(codeParserToken);
  }

  @TokenExtractor
  public static String extractContentsAsString(TypedToken<CodeParser> codeParserToken) {
      return CodeParser.extractContentsAsString(codeParserToken);
  }


}
