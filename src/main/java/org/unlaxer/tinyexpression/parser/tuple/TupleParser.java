package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.type.TypeParametersParser;

public class TupleParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(TupleNameParser.class),
        new WordParser("<"),
        Parser.get(TypeParametersParser.class),
        new WordParser(">")
    );
  }
}