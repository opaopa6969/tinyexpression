package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.EndOfLineParser;
import org.unlaxer.parser.elementary.StartOfLineParser;

public class CodeEndParser extends LazyChain{
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return Parsers.of(
        Parser.get(StartOfLineParser.class),
        Parser.get(TripleBackTickParser.class),
        Parser.get(EndOfLineParser.class)
    );
  }
  
}