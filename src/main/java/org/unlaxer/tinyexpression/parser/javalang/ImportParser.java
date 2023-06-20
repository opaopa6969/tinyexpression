package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.WhiteSpaceDelimitedLazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.parser.posix.SemiColonParser;
import org.unlaxer.tinyexpression.parser.JavaClassMethodParser;

public class ImportParser extends WhiteSpaceDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        new WordParser("import"),
        Parser.get(JavaClassMethodParser.class),
        new WordParser("as"),
        Parser.get(IdentifierParser.class),
        Parser.get(SemiColonParser.class)
    );
  }
}