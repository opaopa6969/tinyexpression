package org.unlaxer.tinyexpression.parser.list;

import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazySeparatedValuesOneOrMore;
import org.unlaxer.tinyexpression.parser.map.MapExpressionParser;

public class CommaSeparatedMapExpressionsParser extends JavaStyleDelimitedLazySeparatedValuesOneOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ()->Parser.get(MapExpressionParser.class);
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }

  @Override
  public Supplier<Parser> getSeparatorParser() {
    return CommaParser::new;
  }

}
