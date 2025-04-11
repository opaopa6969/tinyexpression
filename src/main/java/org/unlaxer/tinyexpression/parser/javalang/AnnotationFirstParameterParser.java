package org.unlaxer.tinyexpression.parser.javalang;

import java.util.Optional;
import java.util.function.Supplier;

import org.unlaxer.parser.Parser;
import org.unlaxer.parser.combinator.LazyZeroOrMore;

public class AnnotationFirstParameterParser extends LazyZeroOrMore{

  @Override
  public Supplier<Parser> getLazyParser() {
    return ()->
        Parser.get(AnnotationParameterParser.class);
  }

  @Override
  public Optional<Parser> getLazyTerminatorParser() {
    return Optional.empty();
  }
}