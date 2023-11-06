package org.unlaxer.tinyexpression.parser.map;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ImmediatelyMapCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new WordParser("new"),
          new WordParser("Map"),
          new WordParser("("),
          new WordParser(")"),
          Parser.get(MapCreationParser.class)
      );
    }
  }