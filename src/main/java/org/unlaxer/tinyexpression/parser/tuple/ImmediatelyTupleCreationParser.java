package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ImmediatelyTupleCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new WordParser("new"),
          new WordParser("Tuple"),
          new WordParser("("),
          new WordParser(")"),
          Parser.get(TupleCreationParser.class)
      );
    }
  }