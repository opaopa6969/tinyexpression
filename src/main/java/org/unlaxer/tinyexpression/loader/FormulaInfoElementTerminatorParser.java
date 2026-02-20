package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class FormulaInfoElementTerminatorParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(EndOfPartParser.class),
        Parser.get(FormulaInfoElementHeaderParser.class)
//        Parser.get(EndOfSourceParser.class)
    );
  }
}