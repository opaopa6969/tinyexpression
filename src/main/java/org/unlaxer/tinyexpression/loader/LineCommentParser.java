package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.LineTerminatorParser;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.elementary.WildCardStringTerminatorParser;
import org.unlaxer.parser.posix.HashParser;

public class LineCommentParser extends LazyChain{
  
  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StartOfLineParser.class),
        Parser.get(HashParser.class),
        new WildCardStringTerminatorParser(false , Parser.get(LineTerminatorParser.class))
    );
  }
}
