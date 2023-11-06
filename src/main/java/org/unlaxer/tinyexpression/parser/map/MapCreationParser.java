package org.unlaxer.tinyexpression.parser.map;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Chain;
import org.unlaxer.parser.combinator.ZeroOrMore;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class MapCreationParser extends JavaStyleDelimitedLazyChain{

    @Override
    public Parsers getLazyParsers() {
      return new Parsers(
          new WordParser("{"),
          Parser.get(MapEntryCreationParser.class),
          new ZeroOrMore(
              new Chain(
                  new WordParser(","),
                  Parser.get(MapEntryCreationParser.class)
              )
          ),
          new WordParser("}")
      );
    }
  }