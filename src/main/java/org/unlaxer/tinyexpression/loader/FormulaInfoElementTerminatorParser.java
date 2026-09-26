package org.unlaxer.tinyexpression.loader;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class FormulaInfoElementTerminatorParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(EndOfPartParser.class),
        // Issue #211: also stop in front of a malformed end mark line ("---END_OF_PART---xyz"),
        // which used to become a value line and silently merge the next block into this one.
        Parser.get(EndOfPartMarkParser.class),
        Parser.get(FormulaInfoElementHeaderParser.class)
//        Parser.get(EndOfSourceParser.class)
    );
  }
}