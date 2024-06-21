package org.unlaxer.tinyexpression.parser.javalang;

import org.unlaxer.Token;
import org.unlaxer.TokenPredicators;
import org.unlaxer.parser.Parser;
import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.clang.IdentifierParser;
import org.unlaxer.parser.combinator.LazyChain;
import org.unlaxer.parser.elementary.WordParser;
import org.unlaxer.util.annotation.TokenExtractor;
import org.unlaxer.util.annotation.TokenExtractor.Timing;

public class AnnotationParser extends LazyChain{
  
  @Override
  public org.unlaxer.parser.Parsers getLazyParsers() {
    
    return new Parsers(
        new WordParser("@"),
        Parser.get(IdentifierParser.class),
        // ここをoptionalにすると、数式の前にパラメータなしのannoataionをつけたときに数式なのか、annotation内の数式なのかを区別がつかない
//        new org.unlaxer.parser.combinator.Optional(Parser.get(AnnotationParametersParser.class))
        Parser.get(AnnotationParametersParser.class)
    );
  }
  
  @SuppressWarnings("unchecked")
  @TokenExtractor(timings = Timing.CreateOperatorOperandTree)
  public static Token extractAnnotation(Token thisParserParsed){
    
    return thisParserParsed.newCreatesOf(
      TokenPredicators.parsers(
          IdentifierParser.class,
          AnnotationParametersParser.class
      )
    );
  }

}
