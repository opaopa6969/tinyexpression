package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.LineTerminatorParser;
import org.unlaxer.parser.elementary.WildCardStringTerninatorParser;
import org.unlaxer.parser.elementary.WordParser;

public class LineAnnotationParser extends LazyChain{

  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    return new Parsers(
        new WordParser("@"),
        Parser.get(IdentifierParser.class),
        new WildCardStringTerninatorParser(true,Parser.get(LineTerminatorParser.class))
    );
  }
}