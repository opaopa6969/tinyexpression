package org.unlaxer.tinyexpression.parser.tuple;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberVariableDeclarationParser.ExpressionChoiceParser;

public class TupleCreationParser extends JavaStyleDelimitedLazyChain {

  @Override
  public List<Parser> getLazyParsers() {
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

