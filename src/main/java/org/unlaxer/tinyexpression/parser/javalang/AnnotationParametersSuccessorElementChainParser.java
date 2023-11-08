package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.posix.CommaParser;

public class AnnotationParametersSuccessorElementChainParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(CommaParser.class),
        Parser.get(AnnotationParameterParser.class)
    );
  }

}