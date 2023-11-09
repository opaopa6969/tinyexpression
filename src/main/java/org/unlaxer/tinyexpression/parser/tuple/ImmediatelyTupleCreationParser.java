package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ImmediatelyTupleCreationParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

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