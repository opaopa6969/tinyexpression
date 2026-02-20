package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Parsed;
import org.unlaxer.Tag;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.EndOfLineParser;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.JavaClassNameParser;

public class CodeStartParser extends LazyChain{

  public enum CodeStartParts{
    scheme,
    codeIdentifier
  }
  
  

  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return Parsers.of(
        Parser.get(StartOfLineParser.class),
        Parser.get(TripleBackTickParser.class),
        Parser.newInstance(IdentifierParser.class).addTag(Tag.of(CodeStartParts.scheme)),
        new WordParser(":"),
        Parser.newInstance(JavaClassNameParser.class).addTag(Tag.of(CodeStartParts.codeIdentifier)),
        Parser.get(EndOfLineParser.class)
    );
  }
}