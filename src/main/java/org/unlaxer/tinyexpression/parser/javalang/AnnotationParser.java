package org.unlaxer.tinyexpression.parser.javalang;

import java.util.List;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.WordParser;

public class AnnotationParser extends LazyChain{
  
  @Override
  public List<Parser> getLazyParsers() {
    
    return new Parsers(
        new WordParser("@"),
        Parser.get(IdentifierParser.class),
        // ここをoptionalにすると、数式の前にパラメータなしのannoataionをつけたときに数式なのか、annotation内の数式なのかを区別がつかない
//        new org.unlaxer.parser.combinator.Optional(Parser.get(AnnotationParametersParser.class))
        Parser.get(AnnotationParametersParser.class)
    );
  }
}
