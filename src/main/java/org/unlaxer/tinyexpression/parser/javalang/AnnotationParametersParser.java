package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;

public class AnnotationParametersParser extends JavaStyleDelimitedLazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(LeftParenthesisParser.class),
        Parser.get(AnnotationFirstParameterParser.class),
        Parser.get(AnnotationParametersSuccessorElementParser.class),
        Parser.get(RightParenthesisParser.class)
    );
  }
}