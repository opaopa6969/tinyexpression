package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.ExpressionChoiceParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class TupleCreationParser extends JavaStyleDelimitedLazyChain {

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("["),
        Parser.get(ExpressionChoiceParser.class),
        new ZeroOrMore(
            new Chain(
                Parser.get(CommaParser.class),
                Parser.get(ExpressionChoiceParser.class)
            )
        ),
        new WordParser("]")
    );
  }
}

