package org.unlaxer.tinyexpression.parser.map;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.type.TypeParametersParser;

public class MapParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(MapNameParser.class),
        new WordParser("<"),
        Parser.get(TypeParametersParser.class),
        new WordParser(">")
    );
  }
}
