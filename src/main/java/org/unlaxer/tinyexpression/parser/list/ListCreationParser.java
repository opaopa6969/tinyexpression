package org.unlaxer.tinyexpression.parser.list;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ListCreationParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
   return new Parsers(
       new WordParser("["),
       Parser.get(ListCreationEntryParser.class),
       new WordParser("]")

   );
  }
}

