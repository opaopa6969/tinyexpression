package org.unlaxer.tinyexpression.parser.list;

import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.posix.CommaParser;
import org.unlaxer.tinyexpression.parser.javalang.JavaStyleDelimitedLazySeparatedValuesOneOrMore;
import org.unlaxer.tinyexpression.parser.string.StringExpressionParser;

public class CommaSeparatedStringExpressionsParser extends JavaStyleDelimitedLazySeparatedValuesOneOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ()->Parser.get(StringExpressionParser.class);
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
