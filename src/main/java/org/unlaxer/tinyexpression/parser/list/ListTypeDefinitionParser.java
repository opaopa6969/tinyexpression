package org.unlaxer.tinyexpression.parser.list;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.type.TypeDefHeaderParser;

public class ListTypeDefinitionParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(TypeDefHeaderParser.class),
        Parser.get(IdentifierParser.class),
        new WordParser("="),
        Parser.get(ListParser.class)
    );
  }
}

