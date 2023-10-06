package org.unlaxer.tinyexpression.parser.tuple;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.Optional;
import org.unlaxer.tinyexpression.parser.AsParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazyChain;

public class TupleTypeHintSuffixParser extends JavaStyleDelimitedLazyChain {


  public TupleTypeHintSuffixParser() {
    super();
  }

  @Override
  public List<Parser> getLazyParsers() {
    return new Parsers(//
        new Optional(
            Parser.get(AsParser.class) //
        ),
        Parser.get(TupleTypeHintParser.class)//
    );
  }
  
}