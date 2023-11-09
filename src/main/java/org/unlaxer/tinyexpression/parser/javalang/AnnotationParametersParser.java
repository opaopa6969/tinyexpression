package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;

public class AnnotationParametersParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(LeftParenthesisParser.class),
        Parser.get(AnnotationFirstParameterParser.class),
        Parser.get(AnnotationParametersSuccessorElementParser.class),
        Parser.get(RightParenthesisParser.class)
    );
  }
}