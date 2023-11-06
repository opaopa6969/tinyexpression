package org.unlaxer.tinyexpression.parser.map;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.ExpressionChoiceParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class MapEntryCreationParser extends JavaStyleDelimitedLazyChain{

  @Override
  public Parsers getLazyParsers() {
    return new Parsers(
        Parser.get(ExpressionChoiceParser.class),
        new WordParser(":"),
        Parser.get(ExpressionChoiceParser.class)
    );
  }
  
}