package org.unlaxer.tinyexpression.parser.type;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class TypeParametersParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(TypeParameterParser.class),
        new ZeroOrMore(
            new Chain(
                Parser.get(CommaParser.class),
                Parser.get(TypeParameterParser.class)
            )
        )
    );
  }
}

