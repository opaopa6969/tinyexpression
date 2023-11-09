package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ImmediatelyListCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new WordParser("new"),
          new WordParser("List"),
          new WordParser("("),
          new WordParser(")"),
          Parser.get(ListCreationParser.class)
      );
    }
  }