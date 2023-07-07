package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.ascii.LeftParenthesisParser;
import org.unlaxer.parser.ascii.RightParenthesisParser;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class MethodInvocationParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new Optional(
            Parser.get(MethodInvocationHeaderParser.class)
        ),
        Parser.get(IdentifierParser.class),
        Parser.get(LeftParenthesisParser.class),
        new Optional(
            Parser.get(ArgumentsParser.class)
        ),
        Parser.get(RightParenthesisParser.class)
    );
  }
  
}