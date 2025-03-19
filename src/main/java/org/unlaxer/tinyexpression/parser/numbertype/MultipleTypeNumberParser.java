package org.unlaxer.tinyexpression.parser.numbertype;

import org.unlaxer.parser.Parsers;
import org.unlaxer.parser.combinator.LazyChoice;

public class MultipleTypeNumberParser extends LazyChoice{

  @Override
  public Parsers getLazyParsers() {

    return new Parsers(

        ShortPrefixNumberParser.class,
        NoSuffixNumberParser.class,
        LongSuffixNumberParser.class,
        LongPrefixNumberParser.class,
        IntPrefixNumberParser.class,
        FloatSuffixNumberParser.class,
        FloatPrefixNumberParser.class,
        DoublePrefixNumberParser.class,
        DoubleSuffixNumberParser.class,
        BytePrefixNumberParser.class,
        BigIntegerPrefixNumberParser.class,
        BigDecimalPrefixNumberParser.class
    );

  }

}