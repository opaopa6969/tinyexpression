package org.unlaxer.tinyexpression.parser.tuple;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;
import org.unlaxer.tinyexpression.parser.number.NumberVariableDeclarationParser.TupleNameParser;
import org.unlaxer.tinyexpression.parser.number.NumberVariableDeclarationParser.TypeParametersParser;

public class TupleParser extends JavaStyleDelimitedLazyChain{

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(
        Parser.get(TupleNameParser.class),
        new WordParser("<"),
        Parser.get(TypeParametersParser.class),
        new WordParser(">")
    );
  }
}