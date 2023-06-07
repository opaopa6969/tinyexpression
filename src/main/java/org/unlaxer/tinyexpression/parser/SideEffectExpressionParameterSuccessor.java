package org.unlaxer.tinyexpression.parser;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class SideEffectExpressionParameterSuccessor extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(CommaParser.class),
        Parser.get(SideEffectExpressionParameterChoice.class)
    );
  }
}