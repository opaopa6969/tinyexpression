package org.unlaxer.tinyexpression.parser.tuple;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.type.TypeDefHeaderParser;

public class TupleTypeDefinitionParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(TypeDefHeaderParser.class),
        Parser.get(IdentifierParser.class),
        new WordParser("="),
        Parser.get(TupleParser.class)
    );
  }
}