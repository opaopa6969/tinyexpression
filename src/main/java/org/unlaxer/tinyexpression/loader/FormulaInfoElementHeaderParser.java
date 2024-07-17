package org.unlaxer.tinyexpression.loader;

import org.unlaxer.TokenPredicators;
import org.unlaxer.TypedToken;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.StartOfLineParser;
import org.unlaxer.parser.posix.ColonParser;
import org.unlaxer.tinyexpression.loader.FormulaInfoParser.Kind;
import org.unlaxer.util.annotation.TokenExtractor;

public class FormulaInfoElementHeaderParser extends LazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(StartOfLineParser.class),
        Parser.get(IdentifierParser.class)
          .addTag(Kind.key.tag()),
        Parser.get(ColonParser.class)
    );
  }
  
  @TokenExtractor
  public String extractKey(TypedToken<FormulaInfoElementHeaderParser> thisParserParsed) {
    String key = thisParserParsed.getChild(TokenPredicators.hasTag(Kind.key.tag())).getToken().orElseThrow();
    return key;
  }
}