package org.unlaxer.tinyexpression.parser.list;

import org.unlaxer.Parsed;
import org.unlaxer.TokenKind;
import org.unlaxer.context.ParseContext;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.AsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class ListTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {


  public ListTypeHintSuffixParser() {
    super();
  }
  
  @Override
  public Parsed parse(ParseContext parseContext, TokenKind tokenKind, boolean invertMatch) {
    return super.parse(parseContext, tokenKind, invertMatch);
  }

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(//
        new Optional(
            Parser.get(AsParser.class) //
        ),
        Parser.get(ListTypeHintParser.class)//
    );
  }
  
}